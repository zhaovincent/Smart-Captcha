<script setup>
import { ref } from 'vue'

/**
 * 角度旋转交互区：拖动圆盘（或使用下方滑杆）把缺口转回正上方。
 * 发出 angle（顺时针为正，可为负值），服务端做环形角度差校验。
 */
const props = defineProps({
  challenge: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['start', 'release'])

const stageEl = ref(null)
const angle = ref(0)
const dragging = ref(false)
const TWO_PI = Math.PI * 2
let prevRad = 0

function pointerAngle(e) {
  const rect = stageEl.value.getBoundingClientRect()
  return Math.atan2(
    e.clientY - (rect.top + rect.height / 2),
    e.clientX - (rect.left + rect.width / 2),
  )
}

function onDown(e) {
  if (props.disabled) return
  dragging.value = true
  prevRad = pointerAngle(e)
  try { e.currentTarget.setPointerCapture?.(e.pointerId) } catch { /* 合成事件或指针已失效时忽略 */ }
  emit('start')
}

function onMove(e) {
  if (!dragging.value) return
  const cur = pointerAngle(e)
  let delta = cur - prevRad
  if (delta > Math.PI) delta -= TWO_PI
  if (delta < -Math.PI) delta += TWO_PI
  angle.value += (delta * 180) / Math.PI
  prevRad = cur
}

function onUp() {
  if (!dragging.value) return
  dragging.value = false
  if (Math.abs(angle.value) > 3 && !props.disabled) {
    emit('release', { angle: Math.round(angle.value * 10) / 10 })
  }
}

function onSliderChange() {
  if (props.disabled) return
  emit('release', { angle: Math.round(angle.value * 10) / 10 })
}

function reset() {
  angle.value = 0
  dragging.value = false
}

defineExpose({ reset })
</script>

<template>
  <div class="rs">
    <div class="rs-stage">
      <div class="rs-marker" aria-hidden="true">
        <svg viewBox="0 0 24 12" width="26" height="13">
          <path d="M2 2h20l-10 9z" fill="#4f46e5" />
        </svg>
      </div>
      <div
        ref="stageEl"
        class="rs-disc-wrap"
        :class="{ 'is-dragging': dragging, 'is-disabled': disabled }"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
        @pointercancel="onUp"
      >
        <img
          class="rs-disc"
          :src="challenge.image"
          alt="角度验证码"
          draggable="false"
          :style="{ transform: `rotate(${angle}deg)` }"
        />
      </div>
    </div>
    <div class="rs-slider-row">
      <span class="rs-slider-icon">↺</span>
      <input
        v-model.number="angle"
        type="range"
        min="-180"
        max="180"
        step="1"
        class="rs-slider"
        :disabled="disabled"
        aria-label="旋转角度滑杆"
        @change="onSliderChange"
      />
      <span class="rs-slider-icon">↻</span>
    </div>
  </div>
</template>

<style scoped>
.rs {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.rs-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.rs-marker {
  height: 14px;
  display: flex;
  align-items: flex-end;
  filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.35));
}

.rs-disc-wrap {
  width: min(220px, 72vw);
  aspect-ratio: 1;
  touch-action: none;
  cursor: grab;
  user-select: none;
}

.rs-disc-wrap.is-dragging {
  cursor: grabbing;
}

.rs-disc-wrap.is-disabled {
  cursor: not-allowed;
  opacity: 0.75;
}

.rs-disc {
  width: 100%;
  height: 100%;
  display: block;
  filter: drop-shadow(0 6px 16px rgba(15, 23, 42, 0.28));
  will-change: transform;
  -webkit-user-drag: none;
}

.rs-slider-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 15px;
}

.rs-slider {
  flex: 1;
  appearance: none;
  height: 6px;
  border-radius: 3px;
  background: linear-gradient(90deg, #c7d2fe, #818cf8);
  outline: none;
}

.rs-slider::-webkit-slider-thumb {
  appearance: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #4f46e5;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.45);
  cursor: grab;
}

.rs-slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #4f46e5;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.45);
  cursor: grab;
}
</style>
