declare const process: { env: { NODE_ENV?: string } };

/** 개발 서버(vite)에서만 true. Vite가 빌드 때 process.env.NODE_ENV를 값으로 바꾼다 (Jest는 import.meta를 못 쓴다). */
export const isDevMode = () => process.env.NODE_ENV === 'development';
