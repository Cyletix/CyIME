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
