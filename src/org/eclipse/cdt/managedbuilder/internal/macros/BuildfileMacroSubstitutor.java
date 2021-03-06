/*******************************************************************************
 * Copyright (c) 2005, 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.macros;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.cdtvariables.CdtVariableException;
import org.eclipse.cdt.core.cdtvariables.ICdtVariable;
import org.eclipse.cdt.core.cdtvariables.ICdtVariableManager;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.managedbuilder.core.IBuildObject;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IInputType;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.IOutputType;
import org.eclipse.cdt.managedbuilder.core.IResourceInfo;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.macros.IBuildMacroProvider;
import org.eclipse.cdt.managedbuilder.macros.IReservedMacroNameSupplier;
import org.eclipse.cdt.utils.cdtvariables.CdtVariableResolver;
import org.eclipse.cdt.utils.cdtvariables.IVariableContextInfo;
import org.eclipse.cdt.utils.cdtvariables.SupplierBasedCdtVariableSubstitutor;
import org.eclipse.cdt.utils.envvar.EnvVarOperationProcessor;
import org.eclipse.core.resources.IResource;

/**
 * This substitutor resolves all macro references except for the environment macro references
 * If a user has chosen to keep those macros in the buildfile, the environment macro references
 * are converted to the buildfile variable references, otherwise those macros are also resolved
 * 
 * @see org.eclipse.cdt.managedbuilder.internal.macros#IMacroSubstitutor
 * @since 3.0
 */
public class BuildfileMacroSubstitutor extends SupplierBasedCdtVariableSubstitutor {
	private static final String PATTERN_MACRO_NAME = "="; //$NON-NLS-1$
	private IConfiguration fConfiguration;
	private IBuilder fBuilder;
	private HashSet<String> fCaseInsensitiveReferencedNames;
	private ICdtVariableManager fVarMngr;
	private ICConfigurationDescription fCfgDes;
	
	private class DefaultReservedMacroNameSupplier implements IReservedMacroNameSupplier{
		String fReservedNames[];
		
		public DefaultReservedMacroNameSupplier(IConfiguration configuration){
			IBuilder builder = configuration.getToolChain().getBuilder();
			String reservedNames[] = builder.getReservedMacroNames();
			String buildVars[] = getConfigurationReservedNames(configuration);

			if(reservedNames == null || reservedNames.length == 0)
				fReservedNames = buildVars;
			else if(buildVars == null || buildVars.length == 0)
				fReservedNames = reservedNames;
			else {
				fReservedNames = new String[reservedNames.length + buildVars.length];
				System.arraycopy(reservedNames,0,fReservedNames,0,reservedNames.length);
				System.arraycopy(buildVars,0,fReservedNames,reservedNames.length,buildVars.length);
			}
		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.managedbuilder.macros.IReservedMacroNameSupplier#isReservedName(java.lang.String, org.eclipse.cdt.managedbuilder.core.IConfiguration)
		 */
		public boolean isReservedName(String macroName, IConfiguration configuration) {
			if(fReservedNames != null && fReservedNames.length > 0){
				for(int i = 0; i < fReservedNames.length; i++){
					Pattern p = Pattern.compile(fReservedNames[i]);
					Matcher m = p.matcher(macroName);
					if(m.matches())
						return true;
				}
			}
			return false;
		}
		
		protected String[] getConfigurationReservedNames(IConfiguration configuration){
			ITool tools[] = configuration.getFilteredTools();
			if(tools != null){
				Set<String> set = new HashSet<String>();
				for(int i = 0; i < tools.length; i++){
					IOutputType ots[] = tools[i].getOutputTypes();
					if(ots != null){
						for(int j = 0; j < ots.length; j++){
							String varName = ots[j].getBuildVariable();
							if(varName != null){
								set.add(varName);
							}
						}
					}

					IInputType its[] = tools[i].getInputTypes();
					if(its != null){
						for(int j = 0; j < its.length; j++){
							String varName = its[j].getBuildVariable();
							if(varName != null){
								set.add(varName);
							}
						}
					}

				}
				
				return set.toArray(new String[set.size()]);
			}
			return null;
		}
	}
	
//	public BuildfileMacroSubstitutor(int contextType, Object contextData, String inexistentMacroValue, String listDelimiter){
//		super(contextType, contextData, inexistentMacroValue, listDelimiter);
//		init();
//	}

	public BuildfileMacroSubstitutor(IBuilder builder, IMacroContextInfo contextInfo, String inexistentMacroValue, String listDelimiter){
		super(contextInfo, inexistentMacroValue, listDelimiter);
		init(builder, contextInfo);
	}

	public BuildfileMacroSubstitutor(IMacroContextInfo contextInfo, String inexistentMacroValue, String listDelimiter){
		this(null, contextInfo, inexistentMacroValue, listDelimiter);
	}
	
	
	
	private void init(IBuilder builder, IMacroContextInfo contextInfo){
		if(contextInfo == null)
			return;
		
		fVarMngr = CCorePlugin.getDefault().getCdtVariableManager();
		
		if(builder != null){
			fBuilder = builder;
			fConfiguration = builder.getParent().getParent();
		} else {
			IBuildObject[] bos = findConfigurationAndBuilderFromContext(contextInfo);
			if(bos != null){
				fConfiguration = (IConfiguration)bos[0];
				fBuilder = (IBuilder)bos[1];
			}
		}
		
		if(fConfiguration != null){
			fCfgDes = ManagedBuildManager.getDescriptionForConfiguration(fConfiguration);
		}
	}
	
	static IBuildObject[] findConfigurationAndBuilderFromContext(IMacroContextInfo contextInfo){
		int type = contextInfo.getContextType();
		IConfiguration cfg = null;
		IBuilder builder = null;
		switch(type){
		case IBuildMacroProvider.CONTEXT_FILE:
			contextInfo = (IMacroContextInfo)contextInfo.getNext();
			if(contextInfo == null)
				break;
		case IBuildMacroProvider.CONTEXT_OPTION:
			contextInfo = (IMacroContextInfo)contextInfo.getNext();
			if(contextInfo == null)
				break;
		case IBuildMacroProvider.CONTEXT_CONFIGURATION:{
				Object contextData = contextInfo.getContextData();
				if(contextData instanceof IConfiguration){
					cfg = (IConfiguration)contextData;
					builder = cfg.getBuilder();
				} else if (contextData instanceof IBuilder){
					builder = (IBuilder)contextData;
					cfg = builder.getParent().getParent();
				} else if (contextData instanceof ITool) {
					ITool tool = (ITool)contextData;
					IResourceInfo rcInfo = tool.getParentResourceInfo();
					if(rcInfo != null){
						cfg = rcInfo.getParent();
						if(cfg != null){
							builder = cfg.getBuilder();
						}
					}
				}
			}
			break;
		case IBuildMacroProvider.CONTEXT_PROJECT:{
				Object contextData = contextInfo.getContextData();
				if(contextData instanceof IManagedProject){
					IResource rc = ((IManagedProject)contextData).getOwner();
					if(rc != null){
						IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(rc);
						cfg = info.getDefaultConfiguration();
						builder = cfg.getBuilder();
					}
				}
			}
			break;
		}
		
		if(cfg != null && builder != null)
			return new IBuildObject[]{cfg, builder};
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.internal.macros.DefaultMacroSubstitutor#resolveMacro(org.eclipse.cdt.managedbuilder.macros.IBuildMacro)
	 */
	@Override
	protected ResolvedMacro resolveMacro(ICdtVariable macro) throws CdtVariableException{
		ResolvedMacro resolved = null;
			
		if(fConfiguration != null && fBuilder != null && 
				fBuilder.keepEnvironmentVariablesInBuildfile() && 
				fVarMngr.isEnvironmentVariable(macro, fCfgDes) &&
				(!CdtVariableResolver.isStringListVariable(macro.getValueType())
						|| size(macro.getStringListValue()) < 2)){
			String ref = getMacroReference(macro);
			if(ref != null)
				resolved = new ResolvedMacro(macro.getName(),ref);

		}
		if(resolved != null)
			return resolved;
		return super.resolveMacro(macro);
	}
	
	private static int size(String[] value){
		return value != null ? value.length : 0;
	}
	
	public IConfiguration getConfiguration(){
		return fConfiguration;
	}
	
	protected IReservedMacroNameSupplier getReservedMacroNameSupplier(){
		if(fBuilder == null)
			return null;
		IReservedMacroNameSupplier supplier = fBuilder.getReservedMacroNameSupplier();
		if(supplier == null)
			supplier = new DefaultReservedMacroNameSupplier(fConfiguration);
		
		return supplier;
	}
	
	protected String getMacroReference(ICdtVariable macro){
		String macroName = macro.getName();
		String ref = null;
		IReservedMacroNameSupplier supplier = getReservedMacroNameSupplier();
		//on win32 all environment variable names are converted to upper case
		macroName = EnvVarOperationProcessor.normalizeName(macroName);
		if(supplier == null || !supplier.isReservedName(macroName,fConfiguration)){
			String pattern = fBuilder.getBuilderVariablePattern();
			if(pattern != null && pattern.indexOf(PATTERN_MACRO_NAME) != -1){
				if(fBuilder.isVariableCaseSensitive() || getCaseInsensitiveReferencedNames().add(macroName.toUpperCase())){
					ref = pattern.replaceAll(PATTERN_MACRO_NAME,macroName);
				}
			}
		}
		return ref;
	}
	
	protected Set<String> getCaseInsensitiveReferencedNames(){
		if(fCaseInsensitiveReferencedNames == null)
			fCaseInsensitiveReferencedNames = new HashSet<String>();
		return fCaseInsensitiveReferencedNames;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.internal.macros.DefaultMacroSubstitutor#setMacroContextInfo(org.eclipse.cdt.managedbuilder.internal.macros.IMacroContextInfo)
	 */
	@Override
	public void setMacroContextInfo(IVariableContextInfo info)
				throws CdtVariableException{
		super.setMacroContextInfo(info);
		if(info instanceof IMacroContextInfo)
			init(null, (IMacroContextInfo)info);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.internal.macros.IMacroSubstitutor#setMacroContextInfo(int, java.lang.Object)
	 */
//	public void setMacroContextInfo(int contextType, Object contextData) throws BuildMacroException{
//		super.setMacroContextInfo(contextType, contextData);
//		init();
//	}

}
