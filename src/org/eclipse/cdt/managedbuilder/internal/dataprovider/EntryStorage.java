/*******************************************************************************
 * Copyright (c) 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.dataprovider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.settings.model.CIncludeFileEntry;
import org.eclipse.cdt.core.settings.model.CIncludePathEntry;
import org.eclipse.cdt.core.settings.model.CLibraryFileEntry;
import org.eclipse.cdt.core.settings.model.CLibraryPathEntry;
import org.eclipse.cdt.core.settings.model.CMacroEntry;
import org.eclipse.cdt.core.settings.model.CMacroFileEntry;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingPathEntry;
import org.eclipse.cdt.core.settings.model.util.EntryNameKey;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.IResourceInfo;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.dataprovider.ProfileInfoProvider.DiscoveredEntry;


public class EntryStorage {
	private int fKind;
	private EntryListMap fDiscoveredEntries = new EntryListMap();
	private EntryListMap fUserEntries = new EntryListMap();
//	private ICLanguageSettingEntry fEntries[];
	private BuildLanguageData fLangData;
	private boolean fCacheInited;
	private boolean fUserValuesInited;
	
	private static final String EMPTY_STRING = new String();

	public EntryStorage(int kind, BuildLanguageData lData){
		fKind = kind;
		fLangData = lData;
	}
	
	public int getKind(){
		return fKind;
	}
	
	void optionsChanged(IOption option, Object oldValue){
		fUserValuesInited = false;
	}
	
	public List getEntries(List list){
		initCache();
		if(list == null)
			list = new ArrayList();
		
		for(Iterator iter = fUserEntries.getIterator(); iter.hasNext();){
			EntryInfo info = (EntryInfo)iter.next();
//			if(!info.isOverridden())
				list.add(info.getEntry());
		}
		for(Iterator iter = fDiscoveredEntries.getIterator(); iter.hasNext();){
			EntryInfo info = (EntryInfo)iter.next();
			if(!info.isOverridden())
				list.add(info.getEntry());
		}
		return list;
	}
	
	private void resetDefaults(){
		resetCache();
		
		IOption options[] = fLangData.getOptionsForKind(fKind);
		ITool tool = fLangData.getTool();
		for(int i = 0; i < options.length; i++){
			IOption option = options[i];
			if(option.getParent() == tool){
				tool.removeOption(option);
			}
		}
	}
	
	private void resetCache(){
		fCacheInited = false;
	}

	public void setEntries(ICLanguageSettingEntry entries[]){
		if(entries == null){
			resetDefaults();
			return;
		}
		initCache();
		ArrayList userList = new ArrayList();
		Map discoveredMap = fDiscoveredEntries.getEntryInfoMap();
		boolean discoveredReadOnly = isDiscoveredEntriesReadOnly();
		
		for(int i = 0; i < entries.length; i++){
			ICLanguageSettingEntry entry = entries[i];
			EntryInfo info = (EntryInfo)discoveredMap.remove(new EntryNameKey(entry));
			if(info == null || info.isOverridden() || !discoveredReadOnly){
				if(info != null){
					info.makeOverridden(true);
				}
				ICLanguageSettingEntry usrEntry = createEntry(entry, false);
				userList.add(usrEntry);
			}
		}
		
		for(Iterator iter = discoveredMap.values().iterator(); iter.hasNext();){
			EntryInfo info = (EntryInfo)iter.next();
			info.makeOverridden(false);
		}
		
		IOption options[] = fLangData.getOptionsForKind(fKind);
		fUserEntries.clear();
		if(options.length > 0){
			IOption option = options[0];
			int size = userList.size();
			String optValue[] = new String[size]; 
			for(int i = 0; i < size; i++){
				ICLanguageSettingEntry entry = (ICLanguageSettingEntry)userList.get(i);
				EntryInfo info = new EntryInfo(entry, false, true);
				fUserEntries.addEntryInfo(info);
				optValue[i] = entryValueToOption(entry);
			}
			
			ITool tool = fLangData.getTool();
			IResourceInfo rcInfo = tool.getParentResourceInfo();
			IOption newOption = ManagedBuildManager.setOption(rcInfo, tool, option, optValue);
			options = fLangData.getOptionsForKind(fKind);
			for(int i = 0; i < options.length; i++){
				if(options[i] != newOption)
					ManagedBuildManager.setOption(rcInfo, tool, option, new String[0]);
			}
		}
	}
	
	private void initCache(){
//		if(fCacheInited){
//			if(!fUserValuesInited){
//				for(Iterator iter = fDiscoveredEntries.getIterator(); iter.hasNext();){
//					EntryInfo info = (EntryInfo)iter.next();
//					info.makeOverridden(false);
//				}
//				initUserValues();
//				fUserValuesInited = true;
//			}
//			
//		} else {
			fCacheInited = true;
			DiscoveredEntry[] dEntries = fLangData.getDiscoveredEntryValues(fKind);
			fDiscoveredEntries.clear();
			boolean readOnly = isDiscoveredEntriesReadOnly();
			if(dEntries.length != 0){
				for(int i = 0; i < dEntries.length; i++){
					DiscoveredEntry dEntry = dEntries[i];
					ICLanguageSettingEntry entry = createEntry(dEntry, true, readOnly);
					EntryInfo info = new EntryInfo(entry, true, false);
					fDiscoveredEntries.addEntryInfo(info);
				}
			}
			initUserValues();
			fUserValuesInited = true;
//		}
	}
	
	private boolean isDiscoveredEntriesReadOnly(){
		if(fKind == ICLanguageSettingEntry.MACRO){
			return fLangData.getOptionsForKind(fKind).length != 0;
		}
		return true;
	}
	
	private void initUserValues(){
		IOption options[] = fLangData.getOptionsForKind(fKind);
		fUserEntries.clear();
		if(options.length > 0){
			for(int i = 0; i < options.length; i++){
				IOption option = options[i];
				List list = (List)option.getValue();
				int size = list.size();
				if(size > 0){
					for(int j = 0; j < size; j++){
						String value = (String)list.get(j);
						ICLanguageSettingEntry entry = createEntry(discoveredEntryFromString(value), false, false);
						EntryInfo discoveredInfo = fDiscoveredEntries.getEntryInfo(entry);
						if(discoveredInfo != null){
//							discoveredInfo.setOptionInfo(option, j);
							discoveredInfo.makeOverridden(true);
						}
						EntryInfo userInfo = new EntryInfo(entry, false, true);
						fUserEntries.addEntryInfo(userInfo);
					}
				}
				
			}
		}
	}
	
	private DiscoveredEntry discoveredEntryFromString(String str){
		if(fKind == ICLanguageSettingEntry.MACRO){
			String nv[] = macroNameValueFromValue(str);
			return new DiscoveredEntry(nv[0], nv[1]);
		}
		return new DiscoveredEntry(str);
	}
	
/*	private List processValues(List valuesList, boolean discovered, List entriesList){
		for(Iterator iter = valuesList.iterator(); iter.hasNext();){
			String value = (String)iter.next();
			ICLanguageSettingEntry entry = createEntry(value, discovered);
			if(entry != null)
				entriesList.add(entry);
		}
		return entriesList;
	}
*/	
	private ICLanguageSettingEntry createEntry(DiscoveredEntry dEntry, boolean discovered, boolean readOnly){
		ICLanguageSettingEntry entry = null;
		int flags = discovered ? ICLanguageSettingEntry.BUILTIN | ICLanguageSettingEntry.READONLY : 0;
		Object v[];
		String value = dEntry.getValue(); 
		String name = dEntry.getName(); 
		switch (fKind){
		case ICLanguageSettingEntry.INCLUDE_PATH:
			v = optionPathValueToEntry(value);
			value = (String)v[0];
			if(((Boolean)v[1]).booleanValue())
				flags |= ICLanguageSettingEntry.VALUE_WORKSPACE_PATH;
			entry = new CIncludePathEntry(value, flags);
			break;
		case ICLanguageSettingEntry.MACRO:
			//String nv[] = macroNameValueFromValue(value);
			
			entry = new CMacroEntry(name, value, flags);
			break;
		case ICLanguageSettingEntry.INCLUDE_FILE:
			v = optionPathValueToEntry(value);
			value = (String)v[0];
			if(((Boolean)v[1]).booleanValue())
				flags |= ICLanguageSettingEntry.VALUE_WORKSPACE_PATH;
			entry = new CIncludeFileEntry(value, flags);
			break;
		case ICLanguageSettingEntry.MACRO_FILE:
			v = optionPathValueToEntry(value);
			value = (String)v[0];
			if(((Boolean)v[1]).booleanValue())
				flags |= ICLanguageSettingEntry.VALUE_WORKSPACE_PATH;
			entry = new CMacroFileEntry(value, flags);
			break;
		case ICLanguageSettingEntry.LIBRARY_PATH:
			v = optionPathValueToEntry(value);
			value = (String)v[0];
			if(((Boolean)v[1]).booleanValue())
				flags |= ICLanguageSettingEntry.VALUE_WORKSPACE_PATH;
			entry = new CLibraryPathEntry(value, flags);
			break;
		case ICLanguageSettingEntry.LIBRARY_FILE:
			v = optionPathValueToEntry(value);
			value = (String)v[0];
			if(((Boolean)v[1]).booleanValue())
				flags |= ICLanguageSettingEntry.VALUE_WORKSPACE_PATH;
			entry = new CLibraryFileEntry(value, flags);
			break;
		}
		return entry;
		
	}

	private ICLanguageSettingEntry createEntry(ICLanguageSettingEntry entry, boolean discovered){
		//ICLanguageSettingEntry entry = null;
		int flags = entry.getFlags();
		if(discovered)
			flags |= ICLanguageSettingEntry.BUILTIN | ICLanguageSettingEntry.READONLY;
		
		switch (fKind){
		case ICLanguageSettingEntry.INCLUDE_PATH:
			entry = new CIncludePathEntry(entry.getName(), flags);
			break;
		case ICLanguageSettingEntry.MACRO:
			entry = new CMacroEntry(entry.getName(), entry.getValue(), flags);
			break;
		case ICLanguageSettingEntry.INCLUDE_FILE:
			entry = new CIncludeFileEntry(entry.getName(), flags);
			break;
		case ICLanguageSettingEntry.MACRO_FILE:
			entry = new CMacroFileEntry(entry.getName(), flags);
			break;
		case ICLanguageSettingEntry.LIBRARY_PATH:
			entry = new CLibraryPathEntry(entry.getName(), flags);
			break;
		case ICLanguageSettingEntry.LIBRARY_FILE:
			entry = new CLibraryFileEntry(entry.getName(), flags);
			break;
		}
		return entry;
		
	}

	private String[] macroNameValueFromValue(String value){
		String nv[] = new String[2];
		int index = value.indexOf('=');
		if(index > 0){
			nv[0] = value.substring(0, index);
			nv[1] = value.substring(index + 1);
		} else {
			nv[0] = value;
			nv[1] = EMPTY_STRING;
		}
		return nv;
	}
	
	private String nameFromValue(String value){
		if(fKind != ICLanguageSettingEntry.MACRO){
			return value;
		}
		return macroNameValueFromValue(value)[0];
	}
	
	private String entryValueToOption(ICLanguageSettingEntry entry){
		if(entry.getKind() == ICLanguageSettingEntry.MACRO && entry.getValue().length() > 0){
			return new StringBuffer(entry.getName()).append('=').append(entry.getValue()).toString();
		} else if(entry instanceof ICLanguageSettingPathEntry){
			ICLanguageSettingPathEntry pathEntry = (ICLanguageSettingPathEntry)entry;
			if(pathEntry.isValueWorkspacePath()){
				return ManagedBuildManager.fullPathToLocation(pathEntry.getValue());
			}
		}
		return entry.getName();
	}
	
	private Object[] optionPathValueToEntry(String value){
		String wspPath = ManagedBuildManager.locationToFullPath(value);
		if(wspPath != null)
			return new Object[]{wspPath, Boolean.valueOf(true)};
		return new Object[]{value, Boolean.valueOf(false)};
	}
	
}