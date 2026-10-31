/** Labels use the saved configuration, never the running server's current defaults. */
export function recordedBotLabel(record, side, fallback = '—') {
  const id = record.botIds?.[side], flags = record.botFlags?.[side];
  if (id && flags && typeof flags === 'object' && !Array.isArray(flags) && Object.keys(flags).length) {
    const enabled = Object.entries(flags).filter(([, value]) => value === true).map(([key]) => `${key}=1`);
    return enabled.length ? `${id} [${enabled.join(', ')}]` : id;
  }
  return record.bots?.[side] || id || fallback;
}
