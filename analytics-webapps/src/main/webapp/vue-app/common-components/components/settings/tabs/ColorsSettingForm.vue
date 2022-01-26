<template>
  <v-form
    ref="form"
    class="generalSettingForm"
    @submit="
      $event.preventDefault();
      $event.stopPropagation();
    ">
    <v-layout wrap xs12>
      <v-flex
        v-for="(color, index) in chartColors"
        :key="index"
        class="mx-auto px-3 my-3"
        xs4>
        <analytics-setting-color-picker
          v-model="colors[index]"
          @input="setColor($event, index)" />
      </v-flex>
    </v-layout>
  </v-form>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    DEFAULT_COLORS: [
      '#319ab3',
      '#f97575',
      '#98cc81',
      '#4273c8',
      '#cea6ac',
      '#bc99e7',
      '#9ee4f5',
      '#774ea9',
      '#ffa500',
      '#bed67e',
      '#0E100F',
      '#ffaacc',
    ],
    colors: [],
  }),
  computed: {
    multipleCharts() {
      return this.settings && (this.settings.multipleChartsField || this.settings.chartType === 'pie');
    },
    chartColors() {
      if (this.multipleCharts) {
        return this.colors  || [];
      } else {
        return this.colors && this.colors.slice(0, 1)  || [];
      }
    },
  },
  methods: {
    init() {
      this.colors = this.settings
        && this.settings.colors
        && this.settings.colors.length
        && this.settings.colors.slice()
        || this.DEFAULT_COLORS;
    },
    setColor(color, index) {
      this.colors.splice(index, 1, color);
      this.settings.colors = this.colors;
    },
  },
};
</script>