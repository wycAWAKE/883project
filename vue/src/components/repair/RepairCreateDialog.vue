<template>
  <dialog ref="dialog" class="repair-dialog" aria-labelledby="repair-create-title" @cancel.prevent="close">
    <header class="repair-dialog-header"><div><p class="repair-eyebrow">Campus facilities</p><h2 id="repair-create-title">Report a fault</h2></div>
      <button type="button" class="repair-button" :disabled="submitting" aria-label="Close report form" @click="close">Close</button></header>
    <p class="repair-muted">Describe the issue and its location. Your account and submission time are added automatically.</p>
    <form class="repair-form" @submit.prevent="$emit('submit')">
      <fieldset :disabled="submitting">
        <label for="repair-title">Title</label>
        <input id="repair-title" v-model="form.title" required maxlength="100" autocomplete="off" :aria-invalid="Boolean(errors.title)" aria-describedby="repair-title-error" placeholder="For example, classroom light is not working" />
        <p v-if="errors.title" id="repair-title-error" class="repair-field-error">{{ errors.title }}</p>
        <label for="repair-location-type">Location type</label>
        <select id="repair-location-type" v-model="form.locationType" required :aria-invalid="Boolean(errors.locationType)" aria-describedby="repair-type-error">
          <option value="">Choose a location type</option><option v-for="item in repairLocations" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <p v-if="errors.locationType" id="repair-type-error" class="repair-field-error">{{ errors.locationType }}</p>
        <label for="repair-location">Location</label>
        <input id="repair-location" v-model="form.locationText" required maxlength="200" :aria-invalid="Boolean(errors.locationText)" aria-describedby="repair-location-error" placeholder="Building, floor and room number" />
        <p v-if="errors.locationText" id="repair-location-error" class="repair-field-error">{{ errors.locationText }}</p>
        <label for="repair-description">Description</label>
        <textarea id="repair-description" v-model="form.description" required maxlength="2000" rows="5" :aria-invalid="Boolean(errors.description)" aria-describedby="repair-description-error" placeholder="What happened, and what needs attention?" />
        <p v-if="errors.description" id="repair-description-error" class="repair-field-error">{{ errors.description }}</p>
      </fieldset>
      <p v-if="error" class="repair-alert repair-alert-error" role="alert">{{ error }}</p>
      <footer class="repair-dialog-actions">
        <button v-if="uncertain" type="button" class="repair-button" :disabled="submitting" @click="$emit('check-reports')">Check my reports</button>
        <button type="button" class="repair-button" :disabled="submitting" @click="close">Cancel</button>
        <button type="submit" class="repair-button repair-primary" :disabled="submitting">{{ submitting ? 'Submitting…' : 'Submit report' }}</button>
      </footer>
    </form>
  </dialog>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { repairLocations } from '../../utils/repair.js'
const props = defineProps({ open: Boolean, form: { type: Object, required: true }, errors: { type: Object, required: true }, submitting: Boolean, error: String, uncertain: Boolean })
const emit = defineEmits(['close', 'submit', 'check-reports'])
const dialog = ref(null)
watch(() => props.open, open => {
  if (open && dialog.value && !dialog.value.open) dialog.value.showModal()
  else if (!open && dialog.value?.open) dialog.value.close()
}, { flush: 'post' })
watch(dialog, element => { if (element && props.open && !element.open) element.showModal() }, { flush: 'post' })
function close() { if (!props.submitting) emit('close') }
onBeforeUnmount(() => { if (dialog.value?.open) dialog.value.close() })
</script>
