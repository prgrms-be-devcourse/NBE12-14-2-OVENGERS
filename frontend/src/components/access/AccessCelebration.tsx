'use client';

import { useEffect, useRef } from 'react';

/** 서버가 출입을 허용한 경우에만 마운트하는 일회성 장식 효과. */
export default function AccessCelebration() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!canvas || motion.matches) return;
    const context = canvas.getContext('2d');
    if (!context) return;

    let width = window.innerWidth;
    let height = window.innerHeight;
    let frame = 0;
    let stopped = false;
    const resize = () => {
      width = window.innerWidth;
      height = window.innerHeight;
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = width * ratio;
      canvas.height = height * ratio;
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
    };
    resize();
    const colors = ['#2864dc', '#78b9ef', '#46c5c1', '#e8b959', '#ae9be0'];
    const particles = Array.from({ length: 160 }, (_, index) => {
      const corner = index % 4;
      const left = corner % 2 === 0;
      const top = corner < 2;
      const speed = Math.min(width, height) * (.38 + Math.random() * .42);
      const angle = .3 + Math.random() * .8;
      return {
        x: left ? 0 : width,
        y: top ? height * .12 : height,
        vx: Math.cos(angle) * speed * (left ? 1 : -1),
        vy: Math.sin(angle) * speed * (top ? 1 : -1.6),
        rotation: Math.random() * Math.PI,
        spin: (Math.random() - .5) * 12,
        size: 5 + Math.random() * 5,
        color: colors[index % colors.length],
      };
    });
    const started = performance.now();
    let previous = started;
    const draw = (now: number) => {
      if (stopped) return;
      const elapsed = (now - started) / 1000;
      const delta = Math.min((now - previous) / 1000, .04);
      previous = now;
      context.clearRect(0, 0, width, height);
      if (elapsed >= 3.2) return;
      context.globalAlpha = Math.min(1, (3.2 - elapsed) / .8);
      for (const particle of particles) {
        particle.vx *= Math.exp(-.55 * delta);
        particle.vy += 380 * delta;
        particle.x += particle.vx * delta;
        particle.y += particle.vy * delta;
        particle.rotation += particle.spin * delta;
        context.save();
        context.translate(particle.x, particle.y);
        context.rotate(particle.rotation);
        context.fillStyle = particle.color;
        context.fillRect(-particle.size / 2, -particle.size / 4, particle.size, particle.size / 2);
        context.restore();
      }
      frame = requestAnimationFrame(draw);
    };
    const stop = () => {
      stopped = true;
      cancelAnimationFrame(frame);
      context.clearRect(0, 0, width, height);
    };
    const onMotionChange = () => { if (motion.matches) stop(); };
    frame = requestAnimationFrame(draw);
    window.addEventListener('resize', resize);
    motion.addEventListener('change', onMotionChange);
    return () => {
      stop();
      window.removeEventListener('resize', resize);
      motion.removeEventListener('change', onMotionChange);
    };
  }, []);

  return <canvas ref={canvasRef} aria-hidden="true" style={{ position: 'fixed', inset: 0, width: '100%', height: '100%', pointerEvents: 'none', zIndex: 100 }} />;
}
