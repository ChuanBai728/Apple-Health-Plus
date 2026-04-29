import { ChatHistoryMessage } from '@/lib/types';

export function ChatBubble({ msg }: { msg: ChatHistoryMessage }) {
  const isUser = msg.role === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-3 ${
          isUser
            ? 'bg-[#007AFF] text-white rounded-br-sm'
            : 'bg-[#E9E9EF] text-[#1C1C1E] rounded-bl-sm'
        }`}
      >
        <p className="whitespace-pre-wrap text-sm">{msg.content}</p>
        {!isUser && msg.evidence && msg.evidence.length > 0 && (
          <div className="mt-2 pt-2 border-t border-black/10 text-xs text-[#3A3A3C]">
            <p className="font-medium mb-1">依据:</p>
            <ul className="list-disc pl-4 space-y-0.5">
              {msg.evidence.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          </div>
        )}
        {!isUser && msg.advice && msg.advice.length > 0 && (
          <div className="mt-2 pt-2 border-t border-black/10 text-xs text-[#3A3A3C]">
            <p className="font-medium mb-1">建议:</p>
            <ul className="list-disc pl-4 space-y-0.5">
              {msg.advice.map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          </div>
        )}
        {!isUser && msg.disclaimer && (
          <p className="mt-2 text-xs text-[#8E8E93]/70 italic">{msg.disclaimer}</p>
        )}
      </div>
    </div>
  );
}
