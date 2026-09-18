export function formatFileSize(value: number) {
  if (value < 1024 * 1024) {
    return `${Math.max(1, Math.round(value / 1024))} KB`
  }

  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function formatNumber(value: number) {
  return new Intl.NumberFormat('vi-VN').format(Number.isFinite(value) ? value : 0)
}
