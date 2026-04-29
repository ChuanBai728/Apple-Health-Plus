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
      <body className="min-h-full bg-[#F2F2F7] text-[#1C1C1E] flex flex-col antialiased" suppressHydrationWarning>
        <Providers>
          <header className="sticky top-0 z-50 bg-white/70 backdrop-blur-xl border-b border-black/5">
            <div className="max-w-[960px] mx-auto px-4 h-12 flex items-center">
              <a href="/" className="text-lg font-bold tracking-tight text-[#007AFF]">
                Apple Health+
              </a>
            </div>
          </header>
          <main className="flex-1 max-w-[960px] mx-auto w-full px-4 py-6">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}
