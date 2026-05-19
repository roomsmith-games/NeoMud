import type { CSSProperties } from 'react';

const style: CSSProperties = {
  fontSize: 10,
  color: '#999',
  marginTop: 2,
};

export default function HelpText({ text }: { text: string }) {
  return <div style={style}>{text}</div>;
}
