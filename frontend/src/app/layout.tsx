import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/Providers";

export const metadata: Metadata = {
  title: "Apple Health+ — 健康数据分析",
  description: "上传 Apple Health 数据，获取可视化健康洞察与 AI 分析",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" className="h-full">
      <body className="min-h-full bg-gray-50 text-gray-900 flex flex-col" suppressHydrationWarning>
        <Providers>
          <header className="bg-white border-b border-gray-200 px-6 py-4">
            <a href="/" className="text-xl font-bold tracking-tight text-blue-600">
              Apple Health+
            </a>
          </header>
          <main className="flex-1 max-w-5xl mx-auto w-full px-4 py-8">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}
