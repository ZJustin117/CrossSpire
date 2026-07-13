const seqNums = new Map<string, number>();

export function nextSeq(sourceId: string): number {
  const current = seqNums.get(sourceId) || 0;
  const next = current + 1;
  seqNums.set(sourceId, next);
  return next;
}

export function getSeq(sourceId: string): number {
  return seqNums.get(sourceId) || 0;
}
