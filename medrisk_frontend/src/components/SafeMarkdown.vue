<template>
  <div class="markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed } from 'vue'

const props = defineProps<{ content?: string }>()

marked.setOptions({
  async: false,
  breaks: true,
  gfm: true
})

const html = computed(() => {
  const parsed = marked.parse(normalizeMarkdown(props.content || '')) as string
  return DOMPurify.sanitize(parsed)
})

function normalizeMarkdown(value: string) {
  return value
    .replace(/\r\n/g, '\n')
    .replace(/^\s*\*{3,}\s*([^*\n].*)$/gm, '### $1')
    .replace(/^\s*\*{3,}\s*$/gm, '---')
    .replace(/^(\s*)#{4,}\s+/gm, '$1### ')
    .replace(/\n{3,}/g, '\n\n')
}
</script>
