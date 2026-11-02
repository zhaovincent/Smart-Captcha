<script setup>
import { computed, ref } from 'vue'

/**
 * 滑块拼图交互区。
 * 对外只暴露 progress（0~1，拼图块行程比例）与耗时，坐标换算交给父组件。
 */
const props = defineProps({
  challenge: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['start', 'release'])

const HANDLE = 42

const trackEl = ref(null)
const progress = ref(0)
const dragging = ref(false)
let startClientX = 0
let baseProgress = 0
let startAt = 0

const slider = computed(() => props.challenge.slider ?? { piece: '', pieceWidth: 0, pieceHeight: 0, y: 0 })

const pieceStyle = computed(() => {
  const c = props.challenge
  const pwPct = (slider.value.pieceWidth / c.width) * 100
  const topPct = (slider.value.y / c.height) * 100
  return {
    width: `${pwPct}%`,
    top: `${topPct}%`,
    left: `calc((100% - ${pwPct}%) * ${progress.value})`,
  }
})

const fillStyle = computed(() => ({
  width: `calc(${HANDLE}px + (100% - ${HANDLE}px) * ${progress.value})`,
}))

const handleStyle = computed(() => ({
  left: `calc((100% - ${HANDLE}px) * ${progress.value})`,
}))

function onDown(e) {
  if (props.disabled) return
  dragging.value = true
  startClientX = e.clientX
  baseProgress = progress.value
  startAt = performance.now()
  try { e.currentTarget.setPointerCapture?.(e.pointerId) } catch { /* 合成事件或指针已失效时忽略 */ }
  emit('start')
}

function onMove(e) {
  if (!dragging.value) return
  const track = trackEl.value
  if (!track) return
  const rect = track.getBoundingClientRect()
  const travel = rect.width - HANDLE
  if (travel <= 0) return
  const delta = e.clientX - startClientX
  progress.value = Math.min(1, Math.max(0, baseProgress + delta / travel))
}

function onUp() {
  if (!dragging.value) return
  dragging.value = false
  if (progress.value > 0.04 && !props.disabled) {
    emit('release', {
      progress: progress.value,
      duration: Math.round(performance.now() - startAt),
    })
  } else {
    progress.value = 0
  }
}

function reset() {
  progress.value = 0
  dragging.value = false
}

defineExpose({ reset })
</script>

<template>
  <div class="ss">
    <div class="ss-canvas">
      <img class="ss-bg" :src="challenge.image" alt="滑块验证码背景" draggable="false" />
      <img
        class="ss-piece"
        :class="{ 'is-ghost': disabled }"
        :src="slider.piece"
        alt=""
        draggable="false"
        :style="pieceStyle"
      />
    </div>
    <div ref="trackEl" class="ss-track" :class="{ 'is-active': dragging }">
      <div class="ss-fill" :style="fillStyle"></div>
      <span v-if="progress <= 0.02" class="ss-hint">按住滑块，拖动完成拼图</span>
      <button
        type="button"
        class="ss-handle"
        :style="handleStyle"
        :disabled="disabled"
        aria-label="拖动滑块完成拼图"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
        @pointercancel="onUp"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            d="M4 12h13m0 0-5-5m5 5-5 5"
            fill="none"
            stroke="currentColor"
            stroke-width="2.4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.ss {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ss-canvas {
  position: relative;
  width: 100%;
  aspect-ratio: var(--captcha-ratio, 1.8);
  border-radius: 10px;
  overflow: hidden;
  background: #eef2f7;
  touch-action: none;
  user-select: none;
}

.ss-bg,
.ss-piece {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  -webkit-user-drag: none;
}

.ss-piece {
  inset: auto;
  height: auto;
  filter: drop-shadow(0 3px 6px rgba(15, 23, 42, 0.35));
  transition: opacity 0.2s;
}

.ss-piece.is-ghost {
  opacity: 0.55;
}

.ss-track {
  position: relative;
  height: 42px;
  border-radius: 21px;
  background: #eef2f7;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.ss-fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: linear-gradient(90deg, #c7d2fe, #818cf8);
  border-radius: 21px;
}

.ss-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  color: #94a3b8;
  pointer-events: none;
}

.ss-handle {
  position: absolute;
  top: 0;
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 21px;
  background: #4f46e5;
  color: #fff;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.45);
  transition: background 0.2s;
}

.ss-handle:active {
  cursor: grabbing;
}

.ss-handle:disabled {
  cursor: not-allowed;
  background: #a5b4fc;
}
</style>
