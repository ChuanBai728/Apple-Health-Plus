import { ChatHistoryMessage } from '@/lib/types';

export function ChatBubble({ msg }: { msg: ChatHistoryMessage }) {
  const isUser = msg.role === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      <div
        className={`max-w-[80%] rounded-xl px-4 py-3 ${
          isUser
            ? 'bg-blue-600 text-white rounded-br-sm'
            : 'bg-gray-100 text-gray-900 rounded-bl-sm'
        }`}
      >
        <p className="whitespace-pre-wrap text-sm">{msg.content}</p>
        {!isUser && msg.evidence && msg.evidence.length > 0 && (
          <div className="mt-2 pt-2 border-t border-gray-300 text-xs text-gray-600">
            <p className="font-medium mb-1">依据:</p>
            <ul className="list-disc pl-4 space-y-0.5">
              {msg.evidence.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          </div>
        )}
        {!isUser && msg.advice && msg.advice.length > 0 && (
          <div className="mt-2 pt-2 border-t border-gray-300 text-xs text-gray-600">
            <p className="font-medium mb-1">建议:</p>
            <ul className="list-disc pl-4 space-y-0.5">
              {msg.advice.map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          </div>
        )}
        {!isUser && msg.disclaimer && (
          <p className="mt-2 text-[10px] text-gray-400 italic">{msg.disclaimer}</p>
        )}
      </div>
    </div>
  );
}
