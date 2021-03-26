import i18n from "i18next";

export function translate(key: string): string {
  return i18n.t(key);
}
