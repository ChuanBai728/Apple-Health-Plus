'use client';

import { useState, useEffect, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { sendChatMessage, getChatSession } from '@/lib/api';
import { ChatMessageResponse, ChatSessionResponse } from '@/lib/types';
import { ChatBubble } from '@/components/ChatBubble';

const SUGGESTED_QUESTIONS = [
  '分析我的整体健康状态',
  '我最近的睡眠质量如何',
  '我的恢复状态怎么样',
  '过去一段时间的活动量有没有下降',
  '我的心率数据正常吗',
];

function generateSessionId(reportId: string, suffix: string = ''): string {
  // Create a deterministic session ID based on reportId for chat
  const hex = reportId.replace(/-/g, '').substring(0, 12);
  return `${hex}${suffix.padEnd(20, '0')}`.substring(0, 32).replace(
    /(.{8})(.{4})(.{4})(.{4})(.{12})/,
    '$1-$2-$3-$4-$5'
  );
}

export default function ChatPage() {
  const { reportId } = useParams<{ reportId: string }>();
  const router = useRouter();
  const chatEndRef = useRef<HTMLDivElement>(null);

  const [sessionId] = useState(() => generateSessionId(reportId));
  const [messages, setMessages] = useState<ChatSessionResponse['messages']>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Load existing session
  useEffect(() => {
    getChatSession(sessionId)
      .then((s) => {
        if (s && s.messages) setMessages(s.messages);
      })
      .catch(() => {
        // Session doesn't exist yet — that's fine
      });
  }, [sessionId]);

  // Auto-scroll
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (question: string) => {
    if (!question.trim() || loading) return;

    const userMsg = { role: 'user', content: question, intent: null, evidence: [], advice: [], disclaimer: null, createdAt: new Date().toISOString() };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);
    setError('');

    try {
      const res: ChatMessageResponse = await sendChatMessage(sessionId, {
        question,
        uploadId: reportId,
      });

      const assistantMsg = {
        role: 'assistant',
        content: res.conclusion,
        intent: res.intent,
        evidence: res.evidence || [],
        advice: res.advice || [],
        disclaimer: res.disclaimer,
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'AI 请求失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4 max-w-3xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => router.push(`/reports/${reportId}/overview`)}
          className="text-[#007AFF] hover:underline text-sm"
        >
          ← 返回概览
        </button>
        <h2 className="text-xl font-bold text-[#1C1C1E]">🤖 AI 健康对话</h2>
        <div className="w-16" />
      </div>

      {/* Chat area */}
      <div className="bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 p-4 min-h-[400px] max-h-[60vh] overflow-y-auto">
        {messages.length === 0 ? (
          <div className="text-center py-12 space-y-6">
            <div className="text-5xl">🤖</div>
            <p className="text-[#8E8E93]">基于你的健康数据，向我提问吧</p>
            <div className="flex flex-wrap gap-2 justify-center">
              {SUGGESTED_QUESTIONS.map((q) => (
                <button
                  key={q}
                  onClick={() => handleSend(q)}
                  disabled={loading}
                  className="px-3 py-1.5 bg-[#F2F2F7] hover:bg-black/[0.06] rounded-full text-sm text-gray-700 disabled:opacity-50 transition-colors"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg, i) => (
            <ChatBubble key={i} msg={msg} />
          ))
        )}
        {loading && (
          <div className="flex items-center gap-2 text-gray-400 py-2">
            <div className="animate-spin h-4 w-4 border-2 border-blue-600 border-t-transparent rounded-full" />
            AI 正在分析...
          </div>
        )}
        {error && <p className="text-[#FF3B30] text-sm">{error}</p>}
        <div ref={chatEndRef} />
      </div>

      {/* Input */}
      <div className="flex gap-3">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSend(input)}
          placeholder="输入健康问题..."
          disabled={loading}
          className="flex-1 px-4 py-3 border border-black/10 rounded-full focus:outline-none focus:ring-2 focus:ring-[#007AFF]/30 disabled:opacity-50"
        />
        <button
          onClick={() => handleSend(input)}
          disabled={loading || !input.trim()}
          className="px-6 py-3 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] disabled:opacity-50 transition-colors font-medium"
        >
          发送
        </button>
      </div>

      <p className="text-center text-xs text-gray-400">
        AI 分析基于你的上传数据，仅供参考，不构成医疗诊断。
      </p>
    </div>
  );
}
