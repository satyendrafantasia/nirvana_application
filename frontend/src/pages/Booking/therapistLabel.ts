const labels: Record<string, string> = {
  INDIAN: 'Indian', THAI: 'Thai', NORTH_EAST: 'Northeast', OTHER: 'Other'
};

export function therapistLabel(type: string) {
  return labels[type.toUpperCase()] || type;
}
