export const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024; // 5MB
export const ALLOWED_IMAGE_MIME_TYPES = ['image/jpeg', 'image/png'] as const;

export const IMAGE_VALIDATION_MESSAGES = {
  EMPTY_FILE: '업로드할 이미지 파일을 선택해 주세요.',
  SIZE_EXCEEDED: '이미지 크기는 최대 5MB까지 업로드할 수 있습니다.',
  UNSUPPORTED_TYPE: 'JPG, PNG 형식의 이미지 파일만 업로드할 수 있습니다.',
} as const;

export function validateImageFile(file: File): string | null {
  if (file.size === 0) {
    return IMAGE_VALIDATION_MESSAGES.EMPTY_FILE;
  }
  if (file.size > MAX_IMAGE_FILE_SIZE) {
    return IMAGE_VALIDATION_MESSAGES.SIZE_EXCEEDED;
  }
  if (!ALLOWED_IMAGE_MIME_TYPES.includes(file.type as typeof ALLOWED_IMAGE_MIME_TYPES[number])) {
    return IMAGE_VALIDATION_MESSAGES.UNSUPPORTED_TYPE;
  }
  return null;
}
