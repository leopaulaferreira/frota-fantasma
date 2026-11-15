export function warSample(count: number) {
  return `${count}/4 guerras conhecidas${count < 4 ? " · média parcial" : ""}`;
}
export function collectionAge(captured: string | null | undefined, reference: string) {
  const elapsed = Date.parse(reference) - Date.parse(captured || "");
  if (!Number.isFinite(elapsed) || elapsed < 0) return "horário não confirmado";
  const minutes = Math.floor(elapsed / 60000);
  if (minutes < 1) return "menos de 1 min antes do recorte";
  if (minutes < 60) return `${minutes} min antes do recorte`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min antes do recorte`;
}
