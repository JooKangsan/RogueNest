export default function Avatar({
  color,
  className = "size-9",
}: {
  color: string;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={`relative flex shrink-0 items-center justify-center gap-[18%] rounded-full border-2 border-amber-950/70 ${className}`}
      style={{ backgroundColor: color }}
    >
      <span className="aspect-square w-[16%] rounded-full bg-amber-950" />
      <span className="aspect-square w-[16%] rounded-full bg-amber-950" />
    </span>
  );
}
