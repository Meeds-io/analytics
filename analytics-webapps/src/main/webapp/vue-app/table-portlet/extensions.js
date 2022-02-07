extensionRegistry.registerExtension('AnalyticsTable', 'CellValue', {
  type: 'user',
  options: {
    // Rank of executing 'match' method
    rank: 40,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-table-cell-user-value'],
    // Method complete signature : match: (fieldName, aggregationType, fieldDataType, item) => { ... }
    match: (fieldName, aggregationType) => fieldName === 'userId' && aggregationType === 'TERMS',
  },
});

extensionRegistry.registerExtension('AnalyticsTable', 'CellValue', {
  type: 'space',
  options: {
    // Rank of executing 'match' method
    rank: 50,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-table-cell-space-value'],
    // Method complete signature : match: (fieldName, aggregationType, fieldDataType, item) => { ... }
    match: (fieldName, aggregationType) => fieldName === 'spaceId' && aggregationType === 'TERMS',
  },
});
