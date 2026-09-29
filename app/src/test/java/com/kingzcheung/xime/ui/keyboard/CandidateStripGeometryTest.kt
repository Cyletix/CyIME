package com.kingzcheung.xime.ui.keyboard
import org.junit.Assert.assertEquals
import org.junit.Test
class CandidateStripGeometryTest {
 @Test fun expandedStartsAfterHeaderAndPreservesFilteredIndices() {
  val entries=listOf(CandidateEntry("PS", "", 2),CandidateEntry("输入", "", 5),CandidateEntry("剩余", "", 9))
  assertEquals(listOf(9),remainingCandidates(entries,listOf("PS","输入")).map { it.globalIndex })
  assertEquals(entries,remainingCandidates(entries,listOf("旧候选")))
  assertEquals(entries,remainingCandidates(entries,emptyList()))
 }

 @Test fun fitsActualWidthAndSpacing() {
  assertEquals(2,candidatePrefixCount(listOf(40,70,30),114,4))
  assertEquals(1,candidatePrefixCount(listOf(40,70,30),113,4))
 }
 @Test fun oversizedFirstRemainsAccessible() { assertEquals(1,candidatePrefixCount(listOf(500,20),60,4)) }
 @Test fun emptyAndUnavailable() {
  assertEquals(0,candidatePrefixCount(emptyList(),200,4))
  assertEquals(0,candidatePrefixCount(listOf(20),0,4))
 }
 @Test fun offscreenCandidatesAreNeverMeasured() {
  var measured=0
  val count=candidatePrefixCount(500,114,4) { index -> measured++; listOf(40,70,30)[index] }
  assertEquals(2,count)
  assertEquals(3,measured)
 }
 @Test fun zeroWidthDoesNotMeasureAnyText() {
  assertEquals(0,candidatePrefixCount(500,0,4) { error("offscreen text measured") })
 }
}
