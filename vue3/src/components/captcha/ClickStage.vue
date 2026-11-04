<script setup>
import { computed, ref } from 'vue'

/**
 * 文字点选交互区：按提示顺序点击背景上的文字，点满后自动提交。
 * 坐标换算为原图坐标系后交给父组件。
 */
const props = defineProps({
  challenge: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['start', 'complete'])

const stageEl = ref(null)
const clicked = ref([])

const words = computed(() => props.challenge.click?.words ?? [])

/** 字符集由后端下发（zh / en），提示文案随之切换 */
const charset = computed(() => props.challenge.click?.charset ?? 'zh')
const promptLabel = computed(() => (charset.value === 'en' ? 'Click in order' : '请依次点击'))

function round1(v) {
  return Math.round(v * 10) / 10
}

function onDown(e) {
  if (props.disabled) return
  const el = stageEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const c = props.challenge
  const x = ((e.clientX - rect.left) / rect.width) * c.width
  const y = ((e.clientY - rect.top) / rect.height) * c.height
  if (x < 0 || y < 0 || x > c.width || y > c.height) return

  emit('start')
  clicked.value = [...clicked.value, { x: round1(x), y: round1(y) }]
  if (clicked.value.length >= words.value.length) {
    emit('complete', clicked.value.map((p) => ({ x: p.x, y: p.y })))
  }
}

function dotStyle(p, index) {
  return {
    left: `${(p.x / props.challenge.width) * 100}%`,
    top: `${(p.y / props.challenge.height) * 100}%`,
  }
}

function reset() {
  clicked.value = []
}

defineExpose({ reset })
</script>

<template>
  <div class="cs">
    <div class="cs-prompt">
      <span class="cs-label">{{ promptLabel }}</span>
      <span
        v-for="(word, i) in words"
        :key="`${word}-${i}`"
        class="cs-word"
        :class="{ 'is-done': i < clicked.length, 'is-current': i === clicked.length }"
      >{{ word }}</span>
    </div>
    <div
      ref="stageEl"
      class="cs-canvas"
      :class="{ 'is-disabled': disabled }"
      @pointerdown="onDown"
    >
      <img class="cs-bg" :src="challenge.image" alt="文字点选验证码背景" draggable="false" />
      <span
        v-for="(p, i) in clicked"
        :key="`${p.x}-${p.y}-${i}`"
        class="cs-dot"
        :class="{ 'is-current': i === clicked.length - 1 }"
        :style="dotStyle(p, i)"
      >{{ i + 1 }}</span>
    </div>
  </div>
</template>

<style scoped>
.cs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cs-prompt {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--bc-text-muted, #475569);
}

.cs-label {
  color: var(--bc-text-faint, #64748b);
}

.cs-word {
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--bc-word-bg, #f1f5f9);
  border: 1px solid var(--bc-word-border, #e2e8f0);
  font-size: 17px;
  font-weight: 600;
  color: var(--bc-word-text, #0f172a);
  transition: all 0.15s;
}

.cs-word.is-current {
  border-color: var(--bc-primary, #4f46e5);
  color: var(--bc-primary, #4f46e5);
  box-shadow: 0 0 0 3px var(--bc-primary-ring, rgba(79, 70, 229, 0.15));
}

.cs-word.is-done {
  opacity: 0.4;
  text-decoration: line-through;
}

.cs-canvas {
  position: relative;
  width: 100%;
  aspect-ratio: var(--captcha-ratio, 1.8);
  border-radius: 10px;
  overflow: hidden;
  background: var(--bc-canvas, #eef2f7);
  cursor: pointer;
  touch-action: manipulation;
  user-select: none;
}

.cs-canvas.is-disabled {
  cursor: not-allowed;
  opacity: 0.75;
}

.cs-bg {
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
  -webkit-user-drag: none;
}

.cs-dot {
  position: absolute;
  width: 22px;
  height: 22px;
  margin: -11px 0 0 -11px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.4);
  border: 1.5px solid rgba(255, 255, 255, 0.9);
  background: var(--bc-dot-alt, #312e81);
  pointer-events: none;
}

.cs-dot.is-current {
  background: var(--bc-primary, #4f46e5);
}
</style>
