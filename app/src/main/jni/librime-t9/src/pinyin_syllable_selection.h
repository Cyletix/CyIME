#ifndef CYIME_PINYIN_SYLLABLE_SELECTION_H_
#define CYIME_PINYIN_SYLLABLE_SELECTION_H_

#include <algorithm>
#include <cstdint>
#include <iomanip>
#include <memory>
#include <sstream>
#include <set>
#include <string>
#include <string_view>
#include <vector>
#include <rime/algo/syllabifier.h>
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/dict/dictionary.h>
#include <rime/schema.h>
#include <rime/ticket.h>

namespace cyime {
namespace pinyin_selection {

// A session-local constraint, never a spelling rewrite. The underscore makes
// Rime discard it at the schema boundary. The engine update hook preserves
// an unchanged selected prefix across suffix edits, while clearing a damaged
// prefix immediately so that clearing/retyping cannot revive an old selection.
inline constexpr const char* kProperty = "_cyime_pinyin_syllable_lock_v1";
inline constexpr const char* kAppliedProperty = "_cyime_pinyin_syllable_applied_v1";

struct Lock {
  std::string schema;
  std::string input;
  std::string confirmed_text;
  uint64_t epoch = 0;
  uint32_t dictionary_checksum = 0;
  uint32_t schema_checksum = 0;
  size_t confirmed = 0;
  size_t start = 0;
  size_t end = 0;
  rime::SyllableId syllable = -1;
};

struct Option {
  rime::SyllableId syllable = -1;
  std::string pinyin;
  size_t start = 0;
  size_t end = 0;
  bool selected = false;
};

inline std::string ConfirmedText(const rime::Context& context) {
  const auto confirmed = context.composition().GetConfirmedPosition();
  std::string text;
  for (const auto& segment : context.composition()) {
    if (segment.end > confirmed || segment.status < rime::Segment::kSelected)
      break;
    if (auto candidate = segment.GetSelectedCandidate()) text += candidate->text();
  }
  return text;
}

inline std::string Encode(const Lock& lock) {
  std::ostringstream out;
  out << 1 << ' ' << std::quoted(lock.schema) << ' ' << std::quoted(lock.input)
      << ' ' << std::quoted(lock.confirmed_text) << ' ' << lock.epoch << ' '
      << lock.dictionary_checksum << ' ' << lock.schema_checksum << ' '
      << lock.confirmed << ' ' << lock.start << ' ' << lock.end << ' '
      << lock.syllable;
  return out.str();
}

inline bool Decode(const std::string& value, Lock* lock) {
  if (value.empty() || !lock) return false;
  std::istringstream in(value);
  unsigned version = 0;
  Lock parsed;
  if (!(in >> version >> std::quoted(parsed.schema) >> std::quoted(parsed.input)
        >> std::quoted(parsed.confirmed_text) >> parsed.epoch
        >> parsed.dictionary_checksum >> parsed.schema_checksum
        >> parsed.confirmed >> parsed.start >> parsed.end >> parsed.syllable) ||
      version != 1 || parsed.syllable < 0 || parsed.start >= parsed.end ||
      parsed.end > parsed.input.size() || parsed.confirmed != parsed.start) {
    return false;
  }
  in >> std::ws;
  if (!in.eof()) return false;
  *lock = std::move(parsed);
  return true;
}

inline void ClearLock(rime::Context* context) {
  if (!context) return;
  if (!context->get_property(kProperty).empty()) context->set_property(kProperty, "");
  if (!context->get_property(kAppliedProperty).empty())
    context->set_property(kAppliedProperty, "");
}

// Runs before the engine composes an updated Context. Comparing the protected
// raw prefix at every edit (including Clear) prevents delete/retype ABA, while
// appending or removing only the unselected suffix keeps the chosen reading.
inline void BeforeContextUpdate(rime::Context* context, const rime::Schema* schema) {
  if (!context) return;
  const auto value = context->get_property(kProperty);
  if (value.empty()) return;
  Lock lock;
  if (!schema || !Decode(value, &lock) || context->get_option("ascii_mode") ||
      schema->schema_id() != lock.schema || context->input().empty() ||
      context->input().size() < lock.end ||
      context->input().compare(0, lock.end, lock.input, 0, lock.end) != 0 ||
      context->composition().GetConfirmedPosition() != lock.confirmed ||
      ConfirmedText(*context) != lock.confirmed_text) {
    ClearLock(context);
    return;
  }
  if (lock.input != context->input() || lock.epoch != context->composition_epoch()) {
    lock.input = context->input();
    lock.epoch = context->composition_epoch();
    context->set_property(kProperty, Encode(lock));
    if (!context->get_property(kAppliedProperty).empty())
      context->set_property(kAppliedProperty, "");
  }
}

// OnSelect has paths that call Compose directly, or only Forward when the
// entire input was selected. Do not wait for a subsequent update/translation.
inline void BeforeCandidateSelection(rime::Context* context) {
  ClearLock(context);
}

inline bool Matches(const Lock& lock, const rime::Context& context,
                    const rime::Schema& schema, const rime::Prism& prism) {
  return !context.get_option("ascii_mode") && lock.schema == schema.schema_id() &&
         lock.input == context.input() && lock.epoch == context.composition_epoch() &&
         lock.dictionary_checksum == prism.dict_file_checksum() &&
         lock.schema_checksum == prism.schema_file_checksum() &&
         lock.confirmed == context.composition().GetConfirmedPosition() &&
         lock.confirmed_text == ConfirmedText(context) &&
         lock.start == lock.confirmed;
}

inline bool ReadLock(rime::Context* context, const rime::Schema* schema,
                     const rime::Prism& prism, Lock* lock) {
  if (!context || !schema || !lock) return false;
  const auto value = context->get_property(kProperty);
  if (value.empty()) return false;
  if (Decode(value, lock) && Matches(*lock, *context, *schema, prism)) return true;
  ClearLock(context);
  return false;
}

// Rebuild pointer-bearing indices after pruning edges. Copying the old index
// would retain dangling EdgeProperties pointers into erased syllable paths.
inline void RebuildIndices(rime::SyllableGraph* graph) {
  graph->indices.clear();
  for (const auto& start : graph->edges) {
    for (auto end = start.second.rbegin(); end != start.second.rend(); ++end) {
      for (const auto& spelling : end->second) {
        graph->indices[start.first][spelling.first].push_back(&spelling.second);
      }
    }
  }
}

// Pure graph operation, also used by the native regression probe. Span ends
// include any trailing speller delimiters, exactly as Rime's graph does.
inline bool ConstrainGraph(rime::SyllableGraph* graph, size_t start, size_t end,
                           rime::SyllableId syllable) {
  if (!graph || start >= end || end > graph->input_length) return false;
  auto from = graph->edges.find(start);
  if (from == graph->edges.end()) return false;
  auto to = from->second.find(end);
  if (to == from->second.end()) return false;
  auto target = to->second.find(syllable);
  if (target == to->second.end() || target->second.type >= rime::kAbbreviation ||
      target->second.is_correction) return false;
  auto chosen = target->second;
  chosen.ambiguous_source_positions.clear();

  graph->indices.clear();
  for (auto source = graph->edges.begin(); source != graph->edges.end();) {
    for (auto edge = source->second.begin(); edge != source->second.end();) {
      if (edge->first <= start || source->first >= end) {
        ++edge;
      } else if (source->first == start && edge->first == end) {
        edge->second.clear();
        edge->second.emplace(syllable, chosen);
        ++edge;
      } else {
        edge = source->second.erase(edge);
      }
    }
    if (source->second.empty()) source = graph->edges.erase(source);
    else ++source;
  }
  for (auto vertex = graph->vertices.upper_bound(start);
       vertex != graph->vertices.end() && vertex->first < end;) {
    vertex = graph->vertices.erase(vertex);
  }
  graph->vertices[start] = rime::kNormalSpelling;
  graph->vertices[end] = chosen.type;
  RebuildIndices(graph);
  return true;
}

// Called only in the script translator, after its actual graph has been built.
// A valid lock that cannot be represented must yield no translation; silently
// ignoring it would show candidates for a different pronunciation.
inline bool ApplyGraphLock(rime::Context* context, rime::Schema* schema,
                           rime::Prism& prism, size_t segment_start,
                           rime::SyllableGraph* graph, bool* constrained = nullptr) {
  if (constrained) *constrained = false;
  Lock lock;
  if (!ReadLock(context, schema, prism, &lock)) return true;
  // Moving the composition caret into the chosen syllable pauses this lock.
  // Moving it out again restores the same reading without rewriting any code.
  if (context->caret_pos() < lock.end) return true;
  if (segment_start >= lock.end || segment_start + graph->input_length <= lock.start)
    return true;
  if (segment_start <= lock.start &&
      lock.end <= segment_start + graph->input_length &&
      ConstrainGraph(graph, lock.start - segment_start,
                     lock.end - segment_start, lock.syllable)) {
    if (constrained) *constrained = true;
    context->set_property(kAppliedProperty, Encode(lock));
    return true;
  }
  graph->indices.clear();
  graph->edges.clear();
  graph->vertices.clear();
  graph->interpreted_length = 0;
  return false;
}

// Append a separately syllabified part using offsets into the unchanged raw
// input. Edge indices point inside maps, so only rebuild them after all parts
// have been copied into their final owner.
inline void AppendGraph(const rime::SyllableGraph& part, size_t offset,
                        rime::SyllableGraph* graph) {
  for (const auto& vertex : part.vertices)
    graph->vertices[vertex.first + offset] = vertex.second;
  for (const auto& source : part.edges) {
    for (const auto& edge : source.second) {
      auto& destination = graph->edges[source.first + offset][edge.first + offset];
      for (const auto& spelling : edge.second) {
        auto properties = spelling.second;
        properties.end_pos += offset;
        std::set<size_t> ambiguous;
        for (const auto position : properties.ambiguous_source_positions)
          ambiguous.insert(position + offset);
        properties.ambiguous_source_positions = std::move(ambiguous);
        destination.emplace(spelling.first, std::move(properties));
      }
    }
  }
}

// An explicit syllable boundary must participate BEFORE global pruning. For
// example ni+n must remain available after choosing ni, even though the normal
// unselected graph prefers the complete syllable nin and removes that path.
// Reuse the translator's own builder so completion/correction/strict options
// are identical; no synthetic delimiter or full-pinyin replacement is written.
inline size_t BuildGraph(rime::Syllabifier& builder, const std::string& input,
                         rime::Prism& prism, rime::Context* context,
                         rime::Schema* schema, size_t segment_start,
                         rime::SyllableGraph* graph,
                         rime::SyllableGraphCache* cache, bool* constrained) {
  if (constrained) *constrained = false;
  Lock lock;
  if (!ReadLock(context, schema, prism, &lock) ||
      context->caret_pos() < lock.end || segment_start >= lock.end ||
      segment_start + input.size() <= lock.start) {
    return static_cast<size_t>(builder.BuildSyllableGraph(input, prism, graph, cache));
  }
  if (constrained) *constrained = true;
  const auto fail = [&]() -> size_t {
    graph->indices.clear();
    graph->edges.clear();
    graph->vertices.clear();
    graph->input_length = input.size();
    graph->interpreted_length = 0;
    return 0;
  };
  if (segment_start > lock.start || lock.end > segment_start + input.size() ||
      context->input().compare(segment_start, input.size(), input) != 0) return fail();
  const size_t start = lock.start - segment_start;
  size_t end = lock.end - segment_start;
  // A newly typed delimiter belongs to the preceding graph edge, just as in
  // ordinary Rime syllabification. It need not enlarge the immutable lock span.
  std::string delimiters = " '";
  schema->config()->GetString("speller/delimiter", &delimiters);
  while (end < input.size() && delimiters.find(input[end]) != std::string::npos) ++end;
  rime::SyllableGraph before;
  if (start != 0 && static_cast<size_t>(builder.BuildSyllableGraph(
          input.substr(0, start), prism, &before)) != start) return fail();
  rime::SyllableGraph chosen;
  const size_t chosen_length = end - start;
  if (static_cast<size_t>(builder.BuildSyllableGraph(
          input.substr(start, chosen_length), prism, &chosen)) != chosen_length ||
      !ConstrainGraph(&chosen, 0, chosen_length, lock.syllable)) return fail();
  rime::SyllableGraph after;
  const size_t suffix_length = end < input.size()
      ? static_cast<size_t>(builder.BuildSyllableGraph(input.substr(end), prism, &after))
      : 0;
  graph->indices.clear();
  graph->edges.clear();
  graph->vertices.clear();
  graph->input_length = input.size();
  graph->interpreted_length = end + suffix_length;
  AppendGraph(before, 0, graph);
  AppendGraph(chosen, start, graph);
  AppendGraph(after, end, graph);
  RebuildIndices(graph);
  context->set_property(kAppliedProperty, Encode(lock));
  return graph->interpreted_length;
}

inline std::unique_ptr<rime::Dictionary> OpenDictionary(rime::Schema* schema) {
  if (!schema || !schema->config()) return nullptr;
  auto* config = schema->config();
  auto has_component = [&](const char* path, const std::string& component,
                           bool primary_namespace) {
    const auto list = config->GetList(path);
    if (!list) return false;
    for (const auto& item : *list) {
      const auto value = rime::As<rime::ConfigValue>(item);
      if (!value) continue;
      const auto& name = value->str();
      if (name == component ||
          (primary_namespace && name == component + "@translator") ||
          (!primary_namespace && name.compare(0, component.size() + 1,
                                              component + "@") == 0)) return true;
    }
    return false;
  };
  // Query the primary phonetic translator, not the physical key count. T9 has
  // its own stateful syllable-selection contract and must never enter this API.
  if (!has_component("engine/translators", "script_translator", true) ||
      has_component("engine/processors", "t9_processor", false)) return nullptr;
  std::string alphabet;
  if (!config->GetString("speller/alphabet", &alphabet) ||
      alphabet.find_first_of("abcdefghijklmnopqrstuvwxyz") == std::string::npos ||
      alphabet.find_first_of("0123456789") != std::string::npos) return nullptr;
  auto component = rime::Dictionary::Require("dictionary");
  if (!component) return nullptr;
  std::unique_ptr<rime::Dictionary> dictionary(
      component->Create(rime::Ticket(schema, "translator")));
  if (!dictionary || (!dictionary->loaded() && !dictionary->Load()) ||
      !dictionary->loaded() ||
      !dictionary->prism() || !dictionary->primary_table()) return nullptr;
  // Legacy deployed schemas lack language/scheme metadata. Their dictionary's
  // actual syllabary is the authority, independent of full/double/merged codes.
  // These Mandarin readings distinguish the shared pinyin table from kana and
  // alphabetic word tables without guessing schema or dictionary filenames.
  rime::Syllabary syllabary;
  if (!dictionary->primary_table()->GetSyllabary(&syllabary) ||
      syllabary.count("ni") == 0 || syllabary.count("hao") == 0 ||
      syllabary.count("zhong") == 0) return nullptr;
  return dictionary;
}

inline std::vector<Option> Options(rime::Context* context, rime::Schema* schema,
                                   rime::Dictionary* dictionary) {
  std::vector<Option> result;
  if (!context || !schema || !dictionary || context->get_option("ascii_mode"))
    return result;
  const size_t start = context->composition().GetConfirmedPosition();
  const size_t caret = context->caret_pos();
  if (start >= caret || caret > context->input().size()) return result;
  std::string delimiters = " '";
  schema->config()->GetString("speller/delimiter", &delimiters);
  bool strict = false;
  schema->config()->GetBool("translator/strict_spelling", &strict);
  size_t code_start = start;
  while (code_start < caret && delimiters.find(context->input()[code_start]) != std::string::npos)
    ++code_start;
  if (code_start == caret) return result;
  std::vector<rime::Prism::Match> matches;
  dictionary->prism()->CommonPrefixSearch(
      std::string_view(context->input().data() + code_start, caret - code_start), &matches);
  Lock selected;
  const bool has_selected = ReadLock(context, schema, *dictionary->prism(), &selected);
  std::set<std::pair<size_t, rime::SyllableId>> seen;
  // Query the authoritative prism before whole-input pruning: choosing ni from
  // nin is legitimate and locks ni+n rather than making ni disappear from UI.
  for (const auto& match : matches) {
    if (match.length == 0 || match.length > caret - code_start) continue;
    size_t end = code_start + match.length;
    while (end < caret && delimiters.find(context->input()[end]) != std::string::npos) ++end;
    for (auto spelling = dictionary->prism()->QuerySpelling(match.value);
         !spelling.exhausted(); spelling.Next()) {
      const auto properties = spelling.properties();
      if (properties.type >= rime::kAbbreviation || properties.is_correction ||
          (strict && end == caret && properties.type != rime::kNormalSpelling)) continue;
      const auto syllable = spelling.syllable_id();
      if (!seen.emplace(end, syllable).second) continue;
      const auto pinyin = dictionary->primary_table()->GetSyllableById(syllable);
      if (pinyin.empty()) continue;
      result.push_back({syllable, pinyin, start, end,
          has_selected && selected.start == start &&
          selected.end >= code_start + match.length && selected.end <= end &&
          selected.syllable == syllable});
    }
  }
  std::sort(result.begin(), result.end(), [](const Option& a, const Option& b) {
    return a.end != b.end ? a.end > b.end : a.pinyin < b.pinyin;
  });
  return result;
}

inline bool Select(rime::Context* context, rime::Schema* schema,
                   const std::string& expected_input, const std::string& expected_schema,
                   size_t start, size_t end, rime::SyllableId syllable) {
  if (!context || !schema || context->get_option("ascii_mode") ||
      context->input() != expected_input || schema->schema_id() != expected_schema ||
      start >= end || end > context->caret_pos() ||
      start != context->composition().GetConfirmedPosition()) return false;
  auto dictionary = OpenDictionary(schema);
  if (!dictionary) return false;
  const auto options = Options(context, schema, dictionary.get());
  if (std::none_of(options.begin(), options.end(), [&](const Option& option) {
        return option.start == start && option.end == end && option.syllable == syllable;
      })) return false;

  const auto previous_property = context->get_property(kProperty);
  const auto previous_applied = context->get_property(kAppliedProperty);
  const auto previous_composition = context->composition();
  const auto caret = context->caret_pos();
  const auto confirmed_text = ConfirmedText(*context);
  Lock lock{expected_schema, expected_input, confirmed_text,
            context->composition_epoch(), dictionary->prism()->dict_file_checksum(),
            dictionary->prism()->schema_file_checksum(), start, start, end, syllable};
  const auto encoded = Encode(lock);
  context->set_property(kAppliedProperty, "");
  context->set_property(kProperty, encoded);
  const bool refreshed = context->RefreshNonConfirmedComposition();
  if (refreshed && context->caret_pos() == caret && context->HasMenu() &&
      context->get_property(kAppliedProperty) == encoded &&
      Matches(lock, *context, *schema, *dictionary->prism())) return true;

  // No input/caret/commit API participates in a successful selection. Restore
  // the old constraint and composition if the selected graph has no result.
  context->set_property(kProperty, previous_property);
  context->set_property(kAppliedProperty, previous_applied);
  context->set_composition(rime::Composition(previous_composition));
  return false;
}

}  // namespace pinyin_selection
}  // namespace cyime
#endif  // CYIME_PINYIN_SYLLABLE_SELECTION_H_
