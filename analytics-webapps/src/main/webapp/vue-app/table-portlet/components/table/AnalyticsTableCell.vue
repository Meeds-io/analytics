<template>
  <v-progress-circular
    v-if="loadingValue"
    size="24"
    color="primary"
    indeterminate />
  <i
    v-else-if="errorLoadingValue"
    :title="$t('analytics.errorRetrievingData')"
    class="uiIconColorError"></i>
  <div v-else>
    <template v-if="cellItems.length">
      <div
        v-for="cellItemSingleValue in cellItems"
        :key="cellItemSingleValue.id">
        <analytics-table-cell-value
          :value="cellItemSingleValue.value"
          :item="cellItem"
          :original-value="$analyticsUtils.toFixed(cellItem.value)"
          :cell-value-extension="cellValueExtension"
          :threshold="$analyticsUtils.toFixed(cellItem.threshold)"
          :field="columnField"
          :data-type="columnDataType"
          :aggregation-type="columnAggregationType"
          :percentage="percentage"
          :labels="labels"
          :column="column" />
      </div>
    </template>
    <span v-else>
      -
    </span>
    <template v-if="previousPeriod">
      <span v-if="cellItemPreviousValues.length" class="ml-2">
        <analytics-table-cell-value
          v-for="previousValue in cellItemPreviousValues"
          :key="previousValue.value"
          :item="cellItem"
          :value="previousValue.percentage"
          :previous-value="percentage && `${previousValue.value}%` || previousValue.value"
          :cell-value-extension="cellValueExtension"
          :field="columnField"
          :aggregation-type="columnAggregationType"
          :data-type="columnDataType"
          :percentage="isValueNumber"
          :labels="labels"
          :column="column"
          compare />
      </span>
      <span v-else>
        -
      </span>
    </template>
  </div>
</template>

<script>
export default {
  props: {
    header: {
      type: Object,
      default: function() {
        return null;
      },
    },
    labels: {
      type: Object,
      default: function() {
        return null;
      },
    },
    item: {
      type: Object,
      default: function() {
        return null;
      },
    },
    cellValueExtensions: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    cellItems: [],
  }),
  computed: {
    column() {
      return this.header.column;
    },
    columnField() {
      return this.column && this.column.valueAggregation && this.column.valueAggregation.aggregation && this.column.valueAggregation.aggregation.field;
    },
    columnAggregationType() {
      return this.column && this.column.valueAggregation && this.column.valueAggregation.aggregation && this.column.valueAggregation.aggregation.type;
    },
    previousPeriod() {
      return this.column && this.column.previousPeriod;
    },
    percentage() {
      return this.column && this.column.thresholdAggregation;
    },
    columnDataType() {
      return this.header && this.header.dataType;
    },
    cellItem() {
      return this.item[this.header.value];
    },
    isValueNumber() {
      return this.columnDataType === 'long' || this.columnDataType === 'double' || this.columnDataType === 'int';
    },
    mainColumnValue() {
      return this.item && this.item['column0'];
    },
    mainColumnIdentity() {
      return this.mainColumnValue && this.mainColumnValue.identity;
    },
    cellItemValue() {
      return this.cellItem && this.cellItem.value;
    },
    loadingValue() {
      return this.cellItemValue === 'loadingData';
    },
    errorLoadingValue() {
      return this.cellItemValue === 'errorRetrievingData';
    },
    cellItemValues() {
      if (this.column.userField) {
        return this.mainColumnIdentity && [this.mainColumnIdentity[this.column.userField]] || [];
      } else if (this.column.spaceField) {
        return this.mainColumnIdentity && [this.mainColumnIdentity[this.column.spaceField]] || [];
      } else if (this.columnAggregationType === 'TERMS' && (this.columnField === 'userId' || this.columnField === 'spaceId')) {
        return this.cellItem && (this.cellItem.identity && [this.cellItem.identity] || (this.cellItemValue && this.formatValues(this.cellItemValue))) || [];
      } else if (!this.cellItemValue || this.loadingValue || this.errorLoadingValue) {
        return [];
      } else {
        return this.formatValues(this.cellItem.value, this.cellItem.threshold);
      }
    },
    cellValueExtension() {
      if (this.cellValueExtensions) {
        return Object.values(this.cellValueExtensions)
          .sort((ext1, ext2) => (ext1.rank || 0) - (ext2.rank || 0))
          .find(extension => extension.match && extension.match(this.columnField, this.columnAggregationType, this.columnDataType, this.cellItem)) || null;
      }
      return null;
    },
    cellItemPreviousValues() {
      if (this.previousPeriod && this.cellItem && this.cellItem.key) {
        const previousValues = this.formatValues(this.cellItem.previousValue, this.cellItem.previousThreshold);
        if (this.isValueNumber) {
          if (previousValues.length < 1 || !previousValues[0]) {
            if (this.cellItemValues.length && this.cellItemValues[0]) {
              previousValues[0] = {
                value: '0',
                percentage: 100, // +100%
              };
            } else if (previousValues.length) {
              previousValues.splice(0, previousValues.length);
            }
          } else if (this.cellItemValues.length < 1 || !this.cellItemValues[0]) {
            previousValues[0] = {
              value: previousValues[0],
              percentage: this.percentage && -previousValues[0] || -100, // -100%
            };
          } else if (this.percentage) {
            previousValues[0] = {
              value: previousValues[0],
              percentage: (this.cellItemValues[0] - previousValues[0]),
            };
          } else {
            previousValues[0] = {
              value: previousValues[0],
              percentage: (this.cellItemValues[0] - previousValues[0]) * 100 / previousValues[0],
            };
          }
        }
        return previousValues;
      } else {
        return [];
      }
    },
  },
  watch: {
    cellItemValue(oldValue, newValue) {
      if (oldValue !== newValue) {
        this.computeCellItemValues();
      }
    },
  },
  mounted() {
    this.computeCellItemValues();
  },
  methods: {
    computeCellItemValues() {
      if (this.cellItemValue && !this.loadingValue && !this.errorLoadingValue) {
        this.cellItems = this.cellItemValues && this.cellItemValues.map(cellItemValue => ({
          id: cellItemValue && (cellItemValue.id || cellItemValue.value) || cellItemValue,
          value: cellItemValue,
        })) || [];
      }
    },
    formatValues(itemValue, itemThreshold) {
      if (this.percentage) {
        const value = itemValue && Number(itemValue) || 0;
        const threshold = this.cellItem.threshold && Number(itemThreshold) || 0;
        if (!value) {
          return [0];
        } else if (!threshold) {
          return [100];
        } else {
          return [this.$analyticsUtils.toFixed(value * 100 / threshold)];
        }
      } else if (itemValue && typeof itemValue === 'object' && itemValue.length) {
        return itemValue;
      } else if (itemValue && Number(itemValue)) {
        return [this.$analyticsUtils.toFixed(itemValue)];
      } else {
        return [itemValue];
      }
    }
  },
};
</script>