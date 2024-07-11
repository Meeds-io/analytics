/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import './initComponents.js';
import './extensions.js';

Vue.use(Vuetify);

const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

// getting language of user
const lang = eXo && eXo.env && eXo.env.portal && eXo.env.portal.language || 'en';
const url = `/analytics/i18n/locale.portlet.Analytics?lang=${lang}`;

// Display loading first time the page is displayed
document.dispatchEvent(new CustomEvent('displayTopBarLoading'));

export function init(dataId) {
  // getting locale ressources
  exoi18n.loadLanguageAsync(lang, url)
    .then(i18n => {
      const element = $(`#${dataId}`);
      const retrieveSettingsURL = element.attr('data-settings-url');
      const retrieveMappingsURL = element.attr('data-mappings-url');
      const retrieveFiltersURL = element.attr('data-filters-url');
      const retrieveTableDataUrl = element.attr('data-table-data-url');
      const retrieveFieldValuesUrl = element.attr('data-field-values-url');
      const saveSettingsURL = element.attr('data-save-settings-url');

      new Vue({
        data: () => ({
          retrieveSettingsURL: retrieveSettingsURL,
          retrieveMappingsURL: retrieveMappingsURL,
          retrieveFiltersURL: retrieveFiltersURL,
          retrieveTableDataUrl: retrieveTableDataUrl,
          retrieveFieldValuesUrl: retrieveFieldValuesUrl,
          saveSettingsURL: saveSettingsURL,
        }),
        mounted() {
          // Hide loading toolbar each time a portlet is mounted
          document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        },
        template: `
        <analytics-table-application
           id="${dataId}"
           :retrieve-settings-url="retrieveSettingsURL"
           :retrieve-mappings-url="retrieveMappingsURL"
           :retrieve-filters-url="retrieveFiltersURL"
           :retrieve-table-data-url="retrieveTableDataUrl"
           :retrieve-field-values-url="retrieveFieldValuesUrl"
           :save-settings-url="saveSettingsURL"
           data-id="${dataId}"
           data-settings-url="${retrieveSettingsURL}"
           data-mappings-url="${retrieveMappingsURL}"
           data-filters-url="${retrieveFiltersURL}"
           data-table-data-url="${retrieveTableDataUrl}"
           data-field-values-url="${retrieveFieldValuesUrl}"
           data-save-settings-url="${saveSettingsURL}" />`,
        vuetify,
        i18n
      }).$mount(`#${dataId}`);
    }).finally(() => Vue.prototype.$utils.includeExtensions('AnalyticsTableExtension'));
}
