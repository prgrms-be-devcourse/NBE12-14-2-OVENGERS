'use client';

import { useState } from 'react';

/** 실제 서버 사진만 표시하며, 누락·실패 시 예시 사진으로 대체하지 않는다. */
export default function SpacePhoto({ src, alt, className = '' }: { src?: string | null; alt: string; className?: string }) {
  const [failedSource, setFailedSource] = useState<string | null>(null);
  if (!src || failedSource === src) {
    return <div className={`photo-placeholder ${className}`} role="img" aria-label={`${alt} — 사진 준비 중`}><span>Slot Key</span><small>공간 사진 준비 중</small></div>;
  }
  return <img src={src} alt={alt} className={className} loading="lazy" decoding="async" onError={() => setFailedSource(src)} />;
}
