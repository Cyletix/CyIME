package com.kingzcheung.xime.association
import org.junit.Assert.*
import org.junit.Test
class BaseAssociationModelTest {
 private fun model() = BaseAssociationModel.read("我\t是\t80\t100\n晚上\t好\t20\t25\n上\t去\t10\t30\n".reader())
 @Test fun longestSuffixAndSentenceBoundary() {
  assertEquals("好",model().predict("今天晚上").first().text)
  assertEquals("去",model().predict("上").first().text)
  assertTrue(model().predict("晚上。").isEmpty())
  assertTrue(model().predict("hello").isEmpty())
 }
 @Test fun probabilityScaleAndNoMutationOnQueries() {
  val m=model(); val before=m.predict("我")
  assertEquals(0.8f, before.first().score, 1e-6f)
  repeat(20) { m.predict("晚上") }
  assertEquals(before,m.predict("我"));assertTrue(m.predict("我",0).isEmpty())
 }
 /** 莫 rows: 名/名其妙/非 finish a word begun by 莫 (bits 0,1,3 in file order), 啊 starts the next one. */
 private fun marked() = BaseAssociationModel.readWithWordCompletions(
  "莫\t名\t40\t100\n莫\t名其妙\t30\t100\n莫\t啊\t5\t100\n莫\t非\t20\t100\n吃饭\t了\t30\t100\n吃饭\t店\t10\t100\n".reader(),
  BaseAssociationModel.readWordCompletions("${BaseAssociationModel.WORD_COMPLETIONS_HEADER} x\n莫\tb\n吃饭\t2\n".reader()))
 @Test fun afterWholeWordSkipsRowsThatFinishTheLastWord() {
  val m=marked()
  // A bare character committed alone may still become a word (莫 → 莫名其妙): unchanged.
  assertEquals(listOf("名","名其妙","非","啊"), m.predict("莫").map{it.text})
  assertEquals(listOf("名","名其妙","非","啊"), m.predict("你在吃饭莫").map{it.text})
  // Exact two-character context after a whole word: only the next-word rows.
  assertEquals(listOf("了"), m.predict("我在吃饭",10,afterWholeWord=true).map{it.text})
  // 莫 matched alone for a longer context, and almost all of it finishes words (5 of 95): nothing.
  assertTrue(m.predict("你在吃饭莫",10,afterWholeWord=true).isEmpty())
  // The same row is still used when the context really is that one character.
  assertEquals(listOf("啊"), m.predict("莫",10,afterWholeWord=true).map{it.text})
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsMarksBeyondTheRows() {
  BaseAssociationModel.readWithWordCompletions("我\t是\t80\t100\n".reader(),
   BaseAssociationModel.readWordCompletions("${BaseAssociationModel.WORD_COMPLETIONS_HEADER} x\n我\t2\n".reader()))
 }
 /** The reported case: after 「你在吃饭莫」 the bundled table offered 莫名其妙. */
 @Test fun bundledTableFollowsTheWholePhrase() {
  val dir=java.io.File("src/main/assets/association")
  val table=java.io.File(dir,"lccc-base-v1.bin"); val marks=java.io.File(dir,"lccc-base-v1.completions.gz")
  fun gz(f:java.io.File)=java.util.zip.GZIPInputStream(f.inputStream()).reader(Charsets.UTF_8)
  // The marks belong to exactly this table file (bits count its rows in file order).
  val sha=java.security.MessageDigest.getInstance("SHA-256").digest(table.readBytes()).joinToString(""){"%02x".format(it)}
  assertEquals("${BaseAssociationModel.WORD_COMPLETIONS_HEADER} $sha", gz(marks).useLines{it.first()})
  val m=gz(table).use{t->BaseAssociationModel.readWithWordCompletions(t, gz(marks).use(BaseAssociationModel::readWordCompletions))}
  fun top(context:String,whole:Boolean)=m.predict(context,BaseAssociationModel.MAX_PER_CONTEXT,whole).map{it.text}
  assertTrue(top("你在吃饭莫",true).toString(), top("你在吃饭莫",true).none{it.startsWith("名")||it=="非"||it=="斯科"})
  // Particles and next words after a whole phrase stay as they were.
  assertEquals(top("你在吃饭吗",false).take(5), top("你在吃饭吗",true).take(5))
  assertEquals(top("吃饭",false).take(5), top("吃饭",true).take(5))
  // 你好 → 吗 still first; 你好运/你好像 (finishing 好运/好像) are gone.
  assertEquals("吗", top("你好",true).first()); assertFalse(top("你好",true).contains("运"))
  // A single character committed on its own keeps its word completions (这→个, 我→们, 莫→名其妙).
  assertTrue(top("莫",false).contains("名其妙")); assertEquals(top("这",false), m.predict("这",BaseAssociationModel.MAX_PER_CONTEXT).map{it.text})
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalidCounts() {
  BaseAssociationModel.read("我\t是\t30\t20\n".reader())
 }
 /** The bundled table must rank the everyday continuations of very common characters first. */
 @Test fun bundledTableRanksCommonContinuationsFirst() {
  val asset=java.io.File("src/main/assets/association/lccc-base-v1.bin")
  assertTrue(asset.absolutePath, asset.isFile)
  val m=java.util.zip.GZIPInputStream(asset.inputStream()).reader(Charsets.UTF_8).use(BaseAssociationModel::read)
  fun top(context:String,k:Int)=m.predict(context,k).map{it.text}
  // 这 → 个/是/些 within the first three; 我 → 们/是/的; 你 → 好/们/的; 不 → 是/知道/要
  assertTrue(top("这",3).toString(), top("这",3).containsAll(listOf("个")) && top("这",3).any{it=="是"||it=="些"})
  assertTrue(top("我",3).toString(), top("我",3).any{it in setOf("们","是","的")})
  assertTrue(top("你",3).toString(), top("你",3).any{it in setOf("好","们","的")})
  assertTrue(top("不",3).toString(), top("不",3).any{it in setOf("是","知道","要")})
  // No continuation longer than two characters may lead a single-character context.
  listOf("这","我","你","不","是","的").forEach { c -> assertTrue(c+top(c,1), top(c,1).first().length<=2) }
 }
}
