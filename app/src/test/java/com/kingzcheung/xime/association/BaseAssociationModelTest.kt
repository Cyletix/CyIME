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
 @Test fun weakPriorAndNoMutationOnQueries() {
  val m=model(); val before=m.predict("我")
  assertTrue(before.first().score in 0f..0.25f)
  repeat(20) { m.predict("晚上") }
  assertEquals(before,m.predict("我"));assertTrue(m.predict("我",0).isEmpty())
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalidCounts() {
  BaseAssociationModel.read("我\t是\t30\t20\n".reader())
 }
}
