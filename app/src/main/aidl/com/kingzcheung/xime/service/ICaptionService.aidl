package com.kingzcheung.xime.service;

import com.kingzcheung.xime.service.ICaptionCallback;

/** 字幕模式会话（:asr 进程）。与键盘语音共用常驻引擎，同一时间只能有一方在用。 */
interface ICaptionService {
    /** 加载模型并开始；speakerModelPath 为空时不分说话人。返回 false 表示模型缺失或加载失败。 */
    boolean start(String modelId, String speakerModelPath, ICaptionCallback callback);
    /** 同上，指定字幕语言：zh 中文 / ja 日语 / en 英语 */
    boolean startLanguage(String modelId, String languageId, String speakerModelPath, ICaptionCallback callback);
    /** 16 kHz / 16 bit / 单声道 PCM */
    void push(IBinder session, in byte[] audioData);
    /** 收尾：未结束的句子按“停止”边界定稿 */
    void stop(IBinder session);
    void cancel(IBinder session);
    /** 把 from 并入 into（from 消失，历史句子由主进程改派）。返回 false 表示任一方不存在。 */
    boolean mergeSpeakers(int from, int into);
    /** 单句改派；句子声纹够长时顺带补进目标说话人 */
    void reassignSegment(long segmentId, int speakerId);
    /** 电视等忽略的声音：以后相似声音直接归它 */
    void setSpeakerIgnored(int speakerId, boolean ignored);
    /** 起过名字的说话人优先匹配 */
    void setSpeakerNamed(int speakerId, boolean named);
    /** 0 宽松 / 1 标准 / 2 严格 */
    void setStrictness(int level);
    /** 清空所有自动分出的声音（用户退出字幕时）；记住的“我”的声音保留 */
    void resetSpeakers();
    /** “这是我”：这个声音是拿手机的人，记在本机，通话字幕以后不显示它 */
    void markSelf(int speakerId);
    /** 忘掉“我”的声音 */
    void forgetSelf();
    /** 永久记住这个人的声音和名字（只存本机）；返回记住后的说话人编号，失败为 -1 */
    int rememberSpeaker(int speakerId, String name);
    /** 忘掉记住的人 */
    void forgetSpeaker(int knownId);
    /** start 返回 false 是因为键盘语音在用：字幕在等，键盘语音说完会收到 onHold(false) */
    boolean isHeld(IBinder session);
}
