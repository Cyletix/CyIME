package com.kingzcheung.xime.settings

enum class CandidateCommentMode { AUTO, SHOW, HIDE }

/** 候选注释写什么；与 [CandidateCommentMode]（何时显示）互相独立。 */
enum class CandidateCommentContent { PINYIN, GLOSS }
