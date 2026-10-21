package com.kingzcheung.xime.service;

/** 字幕模式按句回调：文字与说话人分别到达，已定稿的句子不再被改写。 */
oneway interface ICaptionCallback {
    /** boundary: 0=停顿 1=长度上限 2=停止；confirmed=true 为定稿；event 为 SenseVoice 事件标签（BGM/Laughter…），无则空串 */
    void onSegment(long id, String text, long startMs, long endMs, int boundary, boolean confirmed, String event);
    /** 声纹算完后到达；confident=false 表示“可能是” */
    void onSpeaker(long id, int speakerId, boolean confident);
    void onError(String message);
    /** true：用户点了键盘语音，字幕先让出麦克风和模型；false：键盘语音说完了，字幕自己重新开始 */
    void onHold(boolean held);
}
