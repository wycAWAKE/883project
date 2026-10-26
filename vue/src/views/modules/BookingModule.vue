<template><ModulePlaceholder :module="module" /></template>
<script setup>
import ModulePlaceholder from '../../components/ModulePlaceholder.vue'
defineProps({ module: { type: Object, required: true } })
</script>
