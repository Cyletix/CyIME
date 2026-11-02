// C entry points for CyIME's Japanese converter. The Rime translator (cyime_azookey) calls these;
// Rime keeps the romaji input, segments and selection, this library only converts.
import FoundationEssentials
import KanaKanjiConverterModule
#if canImport(Android)
import Android
#elseif canImport(Glibc)
import Glibc
#endif

private final class Mutex: @unchecked Sendable {
    private var m = pthread_mutex_t()
    init() { pthread_mutex_init(&m, nil) }
    func lock() { pthread_mutex_lock(&m) }
    func unlock() { pthread_mutex_unlock(&m) }
}

private final class Engine: @unchecked Sendable {
    let converter: KanaKanjiConverter
    let memoryURL: URL
    /// Candidates handed to Rime, by id, so a committed one can be learned.
    var issued: [Int32: Candidate] = [:]
    var nextID: Int32 = 1

    init(dictionary: URL, memory: URL) {
        converter = KanaKanjiConverter(dictionaryURL: dictionary, preloadDictionary: false)
        memoryURL = memory
    }

    func options(learning: Bool?) -> ConvertRequestOptions {
        ConvertRequestOptions(
            N_best: 10,
            requireJapanesePrediction: .manualMix,
            requireEnglishPrediction: .disabled,
            keyboardLanguage: .ja_JP,
            englishCandidateInRoman2KanaInput: false,
            fullWidthRomanCandidate: false,
            halfWidthKanaCandidate: false,
            learningType: learning.map { $0 ? .inputAndOutput : .onlyOutput } ?? .nothing,
            maxMemoryCount: 65536,
            shouldResetMemory: false,
            memoryDirectoryURL: memoryURL,
            sharedContainerURL: memoryURL,
            textReplacer: .empty,
            specialCandidateProviders: KanaKanjiConverter.defaultSpecialCandidateProviders,
            metadata: .init(versionString: "CyIME")
        )
    }

    /// Learners type the particles は/へ/を as pronounced (wa/e/o); the dictionary only has them under
    /// ハ/ヘ/ヲ, so 私わ came out. Copy the particle entries, as the dictionary has them in a sentence,
    /// under the pronounced readings (CyIME's former cyime_japanese_particles table did the same in Rime).
    func importPronouncedParticles() {
        let probes: [(romaji: String, particle: String, pronounced: String)] = [
            ("watashiha", "は", "ワ"), ("gakkouhe", "へ", "エ"), ("korewo", "を", "オ"),
        ]
        var entries: [DicdataElement] = []
        for probe in probes {
            var text = ComposingText()
            text.insertAtCursorPosition(probe.romaji, inputStyle: .roman2kana)
            let result = converter.requestCandidates(text, options: options(learning: nil))
            converter.stopComposition()
            guard let element = result.mainResults.lazy.compactMap({ $0.data.last }).first(where: { $0.word == probe.particle }) else { continue }
            entries.append(DicdataElement(word: element.word, ruby: probe.pronounced, lcid: element.lcid, rcid: element.rcid,
                                          mid: element.mid, value: element.value()))
        }
        converter.importDynamicUserDictionary(entries)
    }

    func issue(_ c: Candidate) -> Int32 {
        if issued.count > 4096 { issued.removeAll(keepingCapacity: true) }
        let id = nextID
        nextID = nextID == Int32.max ? 1 : nextID + 1
        issued[id] = c
        return id
    }
}

nonisolated(unsafe) private var engine: Engine?
private let lock = Mutex()

private func cString(_ s: String) -> UnsafeMutablePointer<CChar> { strdup(s)! }

private func json(_ s: String) -> String {
    var out = "\""
    for u in s.unicodeScalars {
        switch u {
        case "\"": out += "\\\""
        case "\\": out += "\\\\"
        case "\n": out += "\\n"
        case "\r": out += "\\r"
        case "\t": out += "\\t"
        default:
            if u.value < 0x20 {
                let hex = String(u.value, radix: 16)
                out += "\\u" + String(repeating: "0", count: 4 - hex.count) + hex
            } else { out.unicodeScalars.append(u) }
        }
    }
    return out + "\""
}

private func kana(_ romaji: Substring) -> String {
    var t = ComposingText()
    t.insertAtCursorPosition(String(romaji), inputStyle: .roman2kana)
    return t.convertTarget
}

/// Opens the dictionary directory (louds/, p/, cb/, mm.binary) and the writable learning directory. 0 on success.
@_cdecl("cyakkc_open")
public func cyakkc_open(_ dictionary: UnsafePointer<CChar>, _ memory: UnsafePointer<CChar>) -> Int32 {
    lock.lock(); defer { lock.unlock() }
    let dictPath = String(cString: dictionary)
    guard FileManager.default.fileExists(atPath: dictPath + "/louds/charID.chid") else { return 1 }
    let mem = URL(fileURLWithPath: String(cString: memory), isDirectory: true)
    try? FileManager.default.createDirectory(at: mem, withIntermediateDirectories: true)
    let e = Engine(dictionary: URL(fileURLWithPath: dictPath, isDirectory: true), memory: mem)
    e.importPronouncedParticles()
    engine = e
    return 0
}

/// Converts lowercase romaji (ASCII; "-" is the long vowel) and returns a JSON array of
/// [text, romajiConsumed, kanaOfConsumedPart, id]. The first entry covers the whole input.
/// Entries that cover only a prefix let Rime confirm a segment and keep converting the rest.
@_cdecl("cyakkc_query")
public func cyakkc_query(_ input: UnsafePointer<CChar>, _ limit: Int32, _ predictions: Int32, _ learning: Int32) -> UnsafeMutablePointer<CChar> {
    lock.lock(); defer { lock.unlock() }
    let romaji = String(cString: input)
    guard let e = engine, !romaji.isEmpty else { return cString("[]") }
    var text = ComposingText()
    text.insertAtCursorPosition(romaji, inputStyle: .roman2kana)
    let result = e.converter.requestCandidates(text, options: e.options(learning: learning != 0))
    let whole = text.convertTarget
    var rows: [String] = []
    var seen = Set<String>()
    // Romaji prefix that spells exactly `surface`; nil when the boundary falls inside one romaji syllable.
    var prefixCache: [Int: Int] = [:]
    func romajiLength(forKanaCount count: Int) -> Int? {
        if count >= whole.count { return romaji.count }
        if let hit = prefixCache[count] { return hit < 0 ? nil : hit }
        let target = whole.prefix(count)
        var found = -1
        for length in 1..<romaji.count where kana(romaji.prefix(length)) == target {
            found = length
            break
        }
        prefixCache[count] = found
        return found < 0 ? nil : found
    }
    func add(_ c: Candidate, coversAll: Bool) {
        let consumed: Int, reading: String
        if coversAll {
            consumed = romaji.count; reading = whole
        } else {
            var rest = text
            rest.prefixComplete(composingCount: c.composingCount)
            let kanaCount = whole.count - rest.convertTarget.count
            guard kanaCount > 0, let length = romajiLength(forKanaCount: kanaCount) else { return }
            consumed = length; reading = String(whole.prefix(kanaCount))
        }
        guard seen.insert("\(consumed)\u{0}\(c.text)").inserted else { return }
        rows.append("[\(json(c.text)),\(consumed),\(json(reading)),\(e.issue(c))]")
    }
    let main = result.mainResults
    let extra = predictions != 0 ? Array(result.predictionResults.prefix(3)) : []
    for (i, c) in main.enumerated() {
        if rows.count >= Int(limit) { break }
        if i == 3 { for p in extra { add(p, coversAll: true) } }
        var rest = text
        rest.prefixComplete(composingCount: c.composingCount)
        add(c, coversAll: rest.convertTarget.isEmpty)
    }
    if main.count <= 3 { for p in extra { add(p, coversAll: true) } }
    return cString("[" + rows.joined(separator: ",") + "]")
}

/// Records that candidate `id` was committed (in order, for word pairs). `cyakkc_end` saves.
@_cdecl("cyakkc_learn")
public func cyakkc_learn(_ id: Int32) {
    lock.lock(); defer { lock.unlock() }
    guard let e = engine, let c = e.issued[id] else { return }
    e.converter.setCompletedData(c)
    e.converter.updateLearningData(c)
}

/// The composition ended (committed or cleared): drop per-input caches and save learning.
@_cdecl("cyakkc_end")
public func cyakkc_end(_ save: Int32) {
    lock.lock(); defer { lock.unlock() }
    guard let e = engine else { return }
    e.converter.stopComposition()
    if save != 0 { e.converter.commitUpdateLearningData() }
    e.issued.removeAll(keepingCapacity: true)
}

@_cdecl("cyakkc_clear_learning")
public func cyakkc_clear_learning() {
    lock.lock(); defer { lock.unlock() }
    engine?.converter.resetMemory()
}

@_cdecl("cyakkc_free")
public func cyakkc_free(_ p: UnsafeMutablePointer<CChar>?) { free(p) }
