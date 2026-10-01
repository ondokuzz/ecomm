/** Joins the class names that apply, skipping the falsy ones. */
export function cx(...names: (string | false | null | undefined)[]): string {
  return names.filter(Boolean).join(' ')
}
