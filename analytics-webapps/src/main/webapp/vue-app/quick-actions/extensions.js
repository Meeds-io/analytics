/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

extensionRegistry.registerExtension('QuickAction', 'Extension', {
  id: 'spacesList',
  icon: 'fa-layer-group',
  name: 'quickActions.spacesList.name',
  description: 'quickActions.spacesList.description',
  click: () => new Promise(resolve => {
    window.require(['SHARED/eXoVueI18n', 'PORTLET/analytics/SpacesListWidget'], exoi18n => initSpacesListDrawer(exoi18n, resolve));
  }),
});

async function initSpacesListDrawer(exoi18n, callback) {
  const appId = 'spaces-list-quick-actions';
  if (!document.querySelector(`#${appId}`)) {
    const parent = document.createElement('div');
    parent.id = appId;
    document.querySelector('#vuetify-apps').appendChild(parent);
    await initSpacesListDrawerApp(appId, exoi18n, eXo.env.portal.maxFileSize);
  }
  document.dispatchEvent(new CustomEvent('quick-action-spaces-list-drawer'));
  callback();
}

function initSpacesListDrawerApp(appId, exoi18n) {
  const lang = eXo.env.portal.language;
  const url = `/analytics/i18n/locale.portlet.Analytics?lang=${lang}`;
  return new Promise(resolve => exoi18n.loadLanguageAsync(lang, url)
    .then(i18n => Vue.createApp({
      template: `
        <spaces-list-widget-drawer
          id="${appId}"
          ref="drawer"
          member-spaces-only />
      `,
      created() {
        document.addEventListener('quick-action-spaces-list-drawer', this.openDrawer);
      },
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        resolve();
      },
      beforeDestroy() {
        document.removeEventListener('quick-action-spaces-list-drawer', this.openDrawer);
      },
      methods: {
        openDrawer() {
          this.$refs.drawer.open();
        },
      },
      vuetify: Vue.prototype.vuetifyOptions,
      i18n,
    }, `#${appId}`, 'Spaces List Quick Action')));
}
