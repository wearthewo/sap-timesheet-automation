"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
export default function WorklistRefresh({ active }: { active: boolean }) {
 const router = useRouter();
 useEffect(() => {
  if (!active) return;
  const timer = setInterval(() => { if (!document.hidden) router.refresh(); }, 10000);
  return () => clearInterval(timer);
 }, [active, router]);
 return null;
}
