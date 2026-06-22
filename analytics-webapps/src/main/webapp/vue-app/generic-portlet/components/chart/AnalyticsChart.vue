<!--
  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <v-flex :id="id" class="analytics-chart-body" />
</template>
<script>
export default {
  props: {
    chartType: {
      type: String,
      default: function() {
        return 'line';
      },
    },
    title: {
      type: String,
      default: function() {
        return null;
      },
    },
    colors: {
      type: Array,
      default: function() {
        return [
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
          '#bc99e7',
          '#ffaacc',
        ];
      },
    },
  },
  data () {
    return {
      id: `Chart${parseInt(Math.random() * 10000)
        .toString()
        .toString()}`,
      chartInstance: null,
      chartOptions: {},
    };
  },
  watch: {
    data() {
      this.init();
    }
  },
  methods: {
    async init(chartsData) {
      const charts = (chartsData && chartsData.charts) || [];
      const labels = await Promise.all(((chartsData && chartsData.labels) || []).map(l => Promise.resolve(this.getI18N(l))));
      const $container = $(`#${this.id}`);
      if (!$container.length) {
        console.error('No chart container for selector', this.id);
        return;
      }
      const series = [];
      const chartOptions = {
        title: {x: 'center'},
        grid: {top: 0, left: 30, bottom: 30},
        color: this.colors,
        series: series,
      };
      if (this.chartType === 'line' || this.chartType === 'bar') {
        const yAxisOptions = {
          type: 'value',
          splitLine: {show: false},
        };

        Object.assign(chartOptions, {
          grid: {show: false},
          tooltip: {
            trigger: 'axis',
            axisPointer: {
              type: 'cross',
              label: {backgroundColor: '#6a7985'}
            },
          },
          xAxis: [{
            type: 'category',
            showGrid: false,
            data: [...labels],
            splitLine: {show: false},
          }],
          yAxis: [yAxisOptions],
        });

        if (this.chartType === 'line') {
          chartOptions.xAxis[0].boundaryGap = false;
        } else {
          chartOptions.xAxis[0].axisTick = {alignWithLabel: true};
        }
        if (charts.length === 1 && (!charts[0].chartLabel || charts[0].chartLabel === 'null')) {
          chartOptions.tooltip.formatter = '<center>{c}</center>';
        }
        const isMultipleCharts = charts.length > 1;
        charts.forEach(chartData => {
          const serie = {
            type: this.chartType,
            data: chartData.values,
            smooth: 0.2,
            showSymbol: false,
          };

          if (!isMultipleCharts) {
            serie.areaStyle = {opacity: 0.8};
            serie.lineStyle = {width: 1};
          }

          if (chartData.chartLabel) {
            this.retrieveI18N(serie, chartData.chartLabel);
          }

          series.push(serie);

          if (chartData.values && chartData.values.length) {
            const chartDataMin = Math.min(...chartData.values);
            if (!yAxisOptions.min || chartDataMin < yAxisOptions.min) {
              yAxisOptions.min = chartDataMin || 0;
            }
            const chartDataMax = Math.max(...chartData.values);
            if (!yAxisOptions.max || chartDataMax > yAxisOptions.max) {
              yAxisOptions.max = chartDataMax || 0;
            }
          }
        });

        if (!yAxisOptions.min) {yAxisOptions.min = 0;}
        if (!yAxisOptions.max) {yAxisOptions.max = 0;}

        yAxisOptions.max += parseInt((yAxisOptions.max - yAxisOptions.min) / 10) + 1;
        yAxisOptions.min -= parseInt((yAxisOptions.max - yAxisOptions.min) / 10);
        if (yAxisOptions.min > 1) {yAxisOptions.min -= 1;}

      } else if (this.chartType === 'pie') {
        chartOptions.tooltip = {trigger: 'item', formatter: '{b} : {c} ({d}%)'};
        chartOptions.legend = {orient: 'vertical', left: '2%'};

        const chartsLength = charts.length;
        const chartsDividerLength = parseInt((chartsLength + 1) / 2) + 1;
        const chartsPercentagePart = parseInt(100 / chartsDividerLength);

        charts.forEach((chartData, index) => {
          const chartDataValues = chartData.aggregationResults.map(result => {
            const chartDataValue = {value: result.result};
            if (result.label) {
              this.retrieveI18N(chartDataValue, result.label);
            }
            return chartDataValue;
          }).sort((v1, v2) => parseFloat(v2.value) - parseFloat(v1.value));

          const xPos = parseInt((parseInt(index / (chartsDividerLength - 1)) + 1) * chartsPercentagePart) * 1.25;
          const yPos = parseInt((parseInt(index % (chartsDividerLength - 1)) + 1) * chartsPercentagePart);

          const serie = {
            type: this.chartType,
            radius: `${chartsPercentagePart + 5}%`,
            center: [`${xPos}%`, `${yPos}%`],
            label: {show: false, position: 'center'},
            data: chartDataValues,
            itemStyle: {
              emphasis: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)',
                label: {show: true, fontSize: '15'},
              },
            },
          };

          let yPiePos;
          if (chartDataValues.length <= 10) {yPiePos = '50%';}
          else if (chartDataValues.length > 10 && chartDataValues.length <= 20) {yPiePos = '65%';}
          else {yPiePos = '80%';}

          if (chartsLength === 1) {
            serie.radius = ['40%', '70%'];
            serie.center = [yPiePos, '45%'];
          }

          serie.name = chartData.chartLabel;
          series.push(serie);
        });
      }

      this.chartOptions = {...chartOptions};
      this.chartOptions.series = [...series];

      if (this.chartInstance) {
        this.chartInstance.dispose();
      }
      this.chartInstance = echarts.init($container[0]);
      this.chartInstance.setOption(this.chartOptions, true);
    },
    retrieveI18N(data, label) {
      const result = this.getI18N(label);
      if (result?.then) {
        result.then(l => {
          data.name = l || label;
          this.chartInstance.setOption(this.chartOptions, true);
        });
      } else {
        data.name = result || label;
      }
    },
    getI18N(label) {
      const fieldName = label.split('=')[0];
      const fieldValue = label.split('=')[1];
      if (fieldValue) {
        const extension = this.$root.fieldNameValueExtensions.find(ext => ext?.match?.(fieldName?.replace?.(/_alt\d*$/, ''), fieldValue));
        if (extension) {
          return extension.getLabel(fieldName, fieldValue);
        } else {
          let fieldLabelI18NKey = `analytics.${fieldValue?.replace?.(/_alt\d*$/, '')}`;
          if (this.$te(fieldLabelI18NKey)) {
            return fieldValue?.includes?.('_alt') ? this.$t('analytics.field.alternative', {0: this.$t(fieldLabelI18NKey)}) : this.$t(fieldLabelI18NKey);
          } else {
            fieldLabelI18NKey = `analytics.field.label.${fieldValue?.replace?.(/_alt\d*$/, '')}`;
            return this.$te(fieldLabelI18NKey) ?
              (fieldValue?.includes?.('_alt') ? this.$t('analytics.field.alternative', {0: this.$t(fieldLabelI18NKey)}) : this.$t(fieldLabelI18NKey)) // NOSONAR
              : label;
          }
          
        }
      } else {
        return label;
      }
    }
  }
};

</script>
