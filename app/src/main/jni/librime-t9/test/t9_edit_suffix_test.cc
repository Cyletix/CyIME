#include <gtest/gtest.h>
#include "t9_undo_model.h"

using rime::SyllableOption;
using rime::T9Buffer;
using rime::T9Segment;
using rime::T9UndoModel;

namespace {
T9UndoModel Selected(const std::string& digits,
                     const std::vector<SyllableOption>& choices) {
    T9UndoModel model;
    for (char digit : digits) model.DigitPressed(digit);
    for (const auto& choice : choices) model.LeftChoice(choice);
    return model;
}

void DeleteWo(T9UndoModel& model) {
    ASSERT_TRUE(model.Backspace());  // undo the newly edited left selection
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    ASSERT_TRUE(model.Backspace());  // delete 6
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    ASSERT_TRUE(model.Backspace());  // delete 9
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
}
}

TEST(T9EditSuffixTest, ReplacesOnlyUnconfirmedSelectionsAndDigits) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.SeparatorPressed(2);
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo'men"));
    EXPECT_EQ("96636", model.ToBuffer().digit_sequence);
    EXPECT_EQ("wo'men", model.ToBuffer().ToRimeInputString());
    EXPECT_TRUE(model.separator_positions().empty());
    EXPECT_FALSE(model.HasPendingCommit());
}

TEST(T9EditSuffixTest, InvalidDraftIsAtomicIncludingCaptureAndUndo) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    model.PushCommitCapture("你", {123});
    for (const auto& invalid : {"ni1", "ni''hao", "'hao", "Ni", "你好"}) {
        EXPECT_FALSE(model.ReplaceEditableSuffix(invalid));
        EXPECT_EQ("64426", model.ToBuffer().digit_sequence);
        EXPECT_EQ("hao", model.ToBuffer().ToRimeInputString());
        ASSERT_EQ(1u, model.commit_captures().size());
        EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    }
    EXPECT_TRUE(model.Backspace());  // original right commit still at top
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
}

TEST(T9EditSuffixTest, PreservesCommittedPrefixAndItsCaptureThenUndoesOriginalWord) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    model.PushCommitCapture("你", {123});
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo"));
    EXPECT_EQ("6496", model.ToBuffer().digit_sequence);
    EXPECT_EQ("wo", model.ToBuffer().ToRimeInputString());
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    ASSERT_EQ(1u, model.commit_captures().size());
    EXPECT_EQ("你", model.commit_captures()[0].first);
    EXPECT_EQ(rime::T9SyllableCode({123}), model.commit_captures()[0].second);
    DeleteWo(model);
    EXPECT_EQ("64", model.ToBuffer().digit_sequence);
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
    EXPECT_EQ("64", model.ToBuffer().unassigned());
    EXPECT_FALSE(model.HasPendingCommit());
    EXPECT_EQ("你", model.PopLastCommitCapture()->first);
    EXPECT_TRUE(model.commit_captures().empty());
}

TEST(T9EditSuffixTest, TailConsumeRetainsOriginalCommittedDigitsWithoutOldSuffix) {
    auto model = Selected("64426", {});
    model.ConsumeTail(2);
    model.PushCommitCapture("你", {123});
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo"));
    DeleteWo(model);
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
    EXPECT_EQ("64", model.ToBuffer().digit_sequence);
    EXPECT_EQ(1u, model.commit_captures().size());
}

TEST(T9EditSuffixTest, LinkedRightCommitDoesNotPreemptNewSuffixDeletion) {
    auto model = Selected("542664", {{"j", 1}});
    model.RightCommit(0);
    model.ConsumeTail(2, true);
    model.PushCommitCapture("加", {234});
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo"));
    DeleteWo(model);
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(2, model.ConsumeUndoneCommitCount());  // existing RC+TC linked accounting
    EXPECT_EQ("542", model.ToBuffer().digit_sequence);
    EXPECT_EQ(T9Segment::kSelected, model.segments()[0].phase);
    EXPECT_EQ(1u, model.commit_captures().size());
}

TEST(T9EditSuffixTest, ReplacingReleasedDigitsNeverReclaimsNewLettersIntoOldWord) {
    auto model = Selected("543", {{"k", 1}, {"he", 2}});
    model.SyncRightCommit(model.ToBuffer(), T9Buffer("543", {}, 2, 3));
    ASSERT_EQ("3", model.tail_digits());
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo"));
    DeleteWo(model);
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
    EXPECT_EQ("54", model.ToBuffer().digit_sequence);
    EXPECT_EQ("4", model.segments()[1].digits);
    EXPECT_EQ(T9Segment::kUnassigned, model.segments()[1].phase);
}

TEST(T9EditSuffixTest, EmptyDraftAndRepeatedEditsKeepAllPriorCommits) {
    auto model = Selected("54482", {{"li", 2}, {"gu", 2}, {"b", 1}});
    model.RightCommit(0);
    model.RightCommit(1);
    model.PushCommitCapture("里", {12});
    model.PushCommitCapture("故", {34});
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo'men"));
    ASSERT_TRUE(model.ReplaceEditableSuffix("hao"));
    ASSERT_TRUE(model.ReplaceEditableSuffix(""));
    EXPECT_EQ("5448", model.ToBuffer().digit_sequence);
    EXPECT_EQ(2u, model.commit_captures().size());
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
    EXPECT_EQ(T9Segment::kSelected, model.segments()[1].phase);
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
}

TEST(T9EditSuffixTest, NewInputAfterEmptyDraftIsDeletedBeforeOldWord) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    ASSERT_TRUE(model.ReplaceEditableSuffix(""));
    model.DigitPressed('9');
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
}

TEST(T9EditSuffixTest, TrailingSeparatorSurvivesEngineAndPreviewProjection) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo'"));
    EXPECT_EQ("wo'", model.ToBuffer().ToRimeInputString());
    EXPECT_EQ("wo'", model.ToBuffer().ToPreeditString());
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    ASSERT_TRUE(model.ReplaceEditableSuffix("wo"));
    EXPECT_EQ("wo", model.ToBuffer().ToRimeInputString());
    EXPECT_TRUE(model.separator_positions().empty());
}

TEST(T9EditSuffixTest, RepeatedEditsDoNotAccumulateDiscardedSegments) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    for (int i = 0; i < 100; ++i) {
        ASSERT_TRUE(model.ReplaceEditableSuffix("wo'men"));
        EXPECT_EQ(3u, model.segments().size());
        EXPECT_EQ("wo'men", model.ToBuffer().ToRimeInputString());
    }
    ASSERT_TRUE(model.ReplaceEditableSuffix(""));
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ(1, model.ConsumeUndoneCommitCount());
}

TEST(T9EditSuffixTest, AppendedDigitsAreDeletedBeforeEditedSyllableSelection) {
    auto model = Selected("64426", {});
    ASSERT_TRUE(model.ReplaceEditableSuffix("ni'hao"));
    model.DigitPressed('6');
    EXPECT_EQ("ni'hao'6", model.ToBuffer().ToRimeInputString());
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ("ni'hao", model.ToBuffer().ToRimeInputString());
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    EXPECT_EQ(T9Segment::kSelected, model.segments()[1].phase);
    model.DigitPressed('2');
    model.DigitPressed('3');
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ("ni'hao'2", model.ToBuffer().ToRimeInputString());
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ("ni'hao", model.ToBuffer().ToRimeInputString());
}

TEST(T9EditSuffixTest, AppendedDigitsDoNotUndoPreviouslyCommittedPrefix) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    model.PushCommitCapture("你", {123});
    ASSERT_TRUE(model.ReplaceEditableSuffix("hao"));
    model.DigitPressed('6');
    ASSERT_TRUE(model.Backspace());
    EXPECT_EQ("hao", model.ToBuffer().ToRimeInputString());
    EXPECT_EQ(T9Segment::kCommitted, model.segments()[0].phase);
    EXPECT_EQ(0, model.ConsumeUndoneCommitCount());
    EXPECT_EQ(1u, model.commit_captures().size());
}

TEST(T9EditSuffixTest, LiveCaretEditPreservesAmbiguousDigitsAndUndoPrefix) {
    auto model = Selected("64426", {{"ni", 2}, {"hao", 3}});
    model.RightCommit(0);
    model.PushCommitCapture("你", {123});
    ASSERT_TRUE(model.ReplaceEditableSuffix("ha6"));
    EXPECT_EQ("ha6", model.ToBuffer().ToRimeInputString());
    EXPECT_EQ("64426", model.ToBuffer().digit_sequence);
    ASSERT_TRUE(model.ReplaceEditableSuffix("hao'6"));
    EXPECT_EQ("hao'6", model.ToBuffer().ToRimeInputString());
    ASSERT_TRUE(model.ReplaceEditableSuffix("hao"));
    EXPECT_EQ("hao", model.ToBuffer().ToRimeInputString());
    ASSERT_EQ(1u, model.commit_captures().size());
    EXPECT_EQ("你", model.commit_captures()[0].first);
}
