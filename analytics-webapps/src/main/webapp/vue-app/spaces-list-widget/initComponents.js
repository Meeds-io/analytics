/*
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
import SpacesListWidget from './components/SpacesListWidget.vue';
import SpacesListWidgetSettingsDrawer from './components/SpacesListWidgetSettingsDrawer.vue';
import SpacesListWidgetDrawer from './components/SpacesListWidgetDrawer.vue';
import SpacesListWidgetList from './components/SpacesListWidgetList.vue';
import SpacesListWidgetItem from './components/SpacesListWidgetItem.vue';

const components = {
  'spaces-list-widget': SpacesListWidget,
  'spaces-list-widget-settings-drawer': SpacesListWidgetSettingsDrawer,
  'spaces-list-widget-drawer': SpacesListWidgetDrawer,
  'spaces-list-widget-list': SpacesListWidgetList,
  'spaces-list-widget-item': SpacesListWidgetItem,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
