# Apply to the already generated script-translator unit. Keep source submodules
# untouched and make the Android/native replay builds consume the same helper.
set(PINYIN_SELECTION_HEADER "${CMAKE_SOURCE_DIR}/librime-t9/src/pinyin_syllable_selection.h")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
             "${CMAKE_CURRENT_LIST_FILE}" "${PINYIN_SELECTION_HEADER}")

t9_replace("#include <rime/gear/poet.h>"
           "#include <rime/gear/poet.h>\n#include <pinyin_syllable_selection.h>")
t9_replace("                    size_t start)\n      : translator_(translator),"
           "                    size_t start, Context* context, Schema* schema)\n      : translator_(translator),")
t9_replace("        start_(start),\n        syllabifier_(translator->delimiters(),"
           "        start_(start),\n        pinyin_context_(context),\n        pinyin_schema_(schema),\n        syllabifier_(translator->delimiters(),")
t9_replace("  Syllabifier syllabifier_;"
           "  Context* pinyin_context_;\n  Schema* pinyin_schema_;\n  bool pinyin_constrained_ = false;\n  Syllabifier syllabifier_;")
t9_replace("  size_t BuildSyllableGraph(Prism& prism);"
           "  size_t BuildSyllableGraph(Prism& prism);\n  bool pinyin_constrained() const { return pinyin_constrained_; }")
t9_replace("double sentence_cutoff_threshold, Config* config)"
           "double sentence_cutoff_threshold, Config* config, Context* context, Schema* schema)")
t9_replace("New<ScriptSyllabifier>(translator, corrector, input, start))"
           "New<ScriptSyllabifier>(translator, corrector, input, start, context, schema))")
t9_replace("sentence_cutoff_threshold_, engine_->schema()->config());"
           "sentence_cutoff_threshold_, engine_->schema()->config(),\n      name_space_ == \"translator\" ? engine_->context() : nullptr, engine_->schema());")
t9_replace([=[  return (size_t)syllabifier_.BuildSyllableGraph(input_, prism,
                                                 &syllable_graph_, cache);]=]
           [=[  const auto consumed = cyime::pinyin_selection::BuildGraph(
      syllabifier_, input_, prism, pinyin_context_, pinyin_schema_, start_,
      &syllable_graph_, cache, &pinyin_constrained_);
  if (pinyin_constrained_) {
    // This must precede Lookup/MakeSentence: old unconstrained buckets must not
    // leak into this selection, or constrained buckets into a later suffix.
    if (auto* inc = translator_->incremental_state()) {
      inc->has_graph = false;
      inc->graph.clear();
      inc->lattice.reset();
    }
  }
  return consumed;]=])

# Both the one-sentence and multi-sentence paths must keep constrained WordGraphs
# out of the incremental cache. Ordinary input retains its existing cache policy.
set(PINYIN_CACHE_ANCHOR "const bool cacheable = !has_completion_edge(syllable_graph);")
string(REGEX MATCHALL "const bool cacheable = !has_completion_edge\\(syllable_graph\\)"
       PINYIN_CACHE_MATCHES "${T9_SCRIPT_TRANSLATOR_CODE}")
list(LENGTH PINYIN_CACHE_MATCHES PINYIN_CACHE_COUNT)
if(NOT PINYIN_CACHE_COUNT EQUAL 2)
  message(FATAL_ERROR "librime sentence cache changed: review both pinyin selection cache guards")
endif()
t9_replace("${PINYIN_CACHE_ANCHOR}"
           "const bool cacheable = !has_completion_edge(syllable_graph) &&\n      !syllabifier_->pinyin_constrained();")

# Central lifecycle hooks cover Context input/delete/clear/caret notifications
# and both candidate-selection paths. Generate a unit rather than editing Rime.
set(PINYIN_ENGINE_SOURCE "${CMAKE_SOURCE_DIR}/librime/src/rime/engine.cc")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${PINYIN_ENGINE_SOURCE}")
file(READ "${PINYIN_ENGINE_SOURCE}" PINYIN_ENGINE_CODE)
function(pinyin_engine_replace old new)
  string(FIND "${PINYIN_ENGINE_CODE}" "${old}" position)
  if(position EQUAL -1)
    message(FATAL_ERROR "librime engine changed: review pinyin lifecycle anchor: ${old}")
  endif()
  string(REPLACE "${old}" "${new}" updated "${PINYIN_ENGINE_CODE}")
  set(PINYIN_ENGINE_CODE "${updated}" PARENT_SCOPE)
endfunction()
pinyin_engine_replace("#include <rime/context.h>"
                      "#include <rime/context.h>\n#include <pinyin_syllable_selection.h>")
pinyin_engine_replace([=[void ConcreteEngine::OnContextUpdate(Context* ctx) {
  if (!ctx)
    return;
  Compose(ctx);
}]=]
                      [=[void ConcreteEngine::OnContextUpdate(Context* ctx) {
  if (!ctx)
    return;
  cyime::pinyin_selection::BeforeContextUpdate(ctx, schema());
  Compose(ctx);
}]=])
pinyin_engine_replace("void ConcreteEngine::OnSelect(Context* ctx) {\n  Segment& seg"
                      "void ConcreteEngine::OnSelect(Context* ctx) {\n  cyime::pinyin_selection::BeforeCandidateSelection(ctx);\n  Segment& seg")
set(PINYIN_ENGINE_COPY "${CMAKE_CURRENT_BINARY_DIR}/cyime-pinyin-engine.cc")
file(CONFIGURE OUTPUT "${PINYIN_ENGINE_COPY}" CONTENT "${PINYIN_ENGINE_CODE}" @ONLY)
get_target_property(PINYIN_ENGINE_SOURCES rime-static SOURCES)
set(PINYIN_ENGINE_REPLACED FALSE)
foreach(source IN LISTS PINYIN_ENGINE_SOURCES)
  if(source MATCHES "(^|/)rime/engine\\.cc$")
    list(REMOVE_ITEM PINYIN_ENGINE_SOURCES "${source}")
    set(PINYIN_ENGINE_REPLACED TRUE)
  endif()
endforeach()
if(NOT PINYIN_ENGINE_REPLACED)
  message(FATAL_ERROR "Cannot locate librime engine compilation unit")
endif()
list(APPEND PINYIN_ENGINE_SOURCES "${PINYIN_ENGINE_COPY}")
set_property(TARGET rime-static PROPERTY SOURCES "${PINYIN_ENGINE_SOURCES}")
