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
package org.eclipse.cdt.managedbuilder.internal.envvar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.core.envvar.IEnvironmentVariableManager;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.internal.core.envvar.EnvVarCollector;
import org.eclipse.cdt.managedbuilder.core.IBuildPathResolver;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IEnvVarBuildPath;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.envvar.IBuildEnvironmentVariable;
import org.eclipse.cdt.managedbuilder.envvar.IEnvironmentBuildPathsChangeListener;
import org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider;
import org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableSupplier;
import org.eclipse.cdt.utils.envvar.EnvVarOperationProcessor;

/**
 * This class implements the IEnvironmentVariableProvider interface and provides all
 * build environment functionality to the MBS
 * 
 * @since 3.0
 *
 */
public class EnvironmentVariableProvider implements
		IEnvironmentVariableProvider {
//	private static final QualifiedName fBuildPathVarProperty = new QualifiedName(ManagedBuilderCorePlugin.getUniqueIdentifier(), "buildPathVar");	//$NON-NLS-1$

//	private static final String DELIMITER_WIN32 = ";";  //$NON-NLS-1$
//	private static final String DELIMITER_UNIX = ":";  //$NON-NLS-1$
	
	private static EnvironmentVariableProvider fInstance = null;
	private List<IEnvironmentBuildPathsChangeListener> fListeners = null;
	private IEnvironmentVariableManager fMngr;
	private boolean fBuildPathVarCheckAllowed;

	private StoredBuildPathEnvironmentContainer fIncludeStoredBuildPathVariables;
	private StoredBuildPathEnvironmentContainer fLibraryStoredBuildPathVariables;
	
	/**
	 * This class is used by the EnvironmentVariableProvider to calculate the build paths
	 * in case a tool-integrator did not provide the special logic for obtaining the build 
	 * paths from environment variable values
	 * 
	 * @since 3.0
	 *
	 */
	static public class DefaultBuildPathResolver implements IBuildPathResolver {
		private String fDelimiter;
		
		public DefaultBuildPathResolver(String delimiter){
			fDelimiter = delimiter;
		}

		public String[] resolveBuildPaths(int pathType, String variableName,
				String variableValue, IConfiguration configuration) {
			
			if(fDelimiter == null || "".equals(fDelimiter)) //$NON-NLS-1$
				return new String[]{variableValue};
			
			List<String> list = EnvVarOperationProcessor.convertToList(variableValue,fDelimiter);
			return list.toArray(new String[list.size()]);
		}

	}
	
	protected EnvironmentVariableProvider(IEnvironmentVariableManager mngr){
		fMngr = mngr;
	}

	public static EnvironmentVariableProvider getDefault(){
		if(fInstance == null){
			fInstance = new EnvironmentVariableProvider(CCorePlugin.getDefault().getBuildEnvironmentManager());
			fInstance.fBuildPathVarCheckAllowed = true;
		}
		return fInstance;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#getVariable()
	 */
	public IBuildEnvironmentVariable getVariable(String variableName,
			Object level, boolean includeParentLevels, boolean resolveMacros) {

		if(variableName == null || "".equals(variableName)) //$NON-NLS-1$
			return null;
		
		if(level instanceof IConfiguration){
			return wrap(getVariable(variableName, (IConfiguration)level, resolveMacros));
		}
		return null;
	}

	public IEnvironmentVariable getVariable(String variableName,
			IConfiguration cfg, boolean resolveMacros){
		return getVariable(variableName, cfg, resolveMacros, true);
	}

	public IEnvironmentVariable getVariable(String variableName,
			IConfiguration cfg, boolean resolveMacros, boolean checkBuildPaths){
		ICConfigurationDescription des = ManagedBuildManager.getDescriptionForConfiguration(cfg);
		if(des != null){
			IEnvironmentVariable variable = fMngr.getVariable(variableName, des, resolveMacros);
			if(checkBuildPaths && resolveMacros && fBuildPathVarCheckAllowed)
				checkBuildPathVariable(cfg, variableName, variable);
			return variable;
		}
		return null;
	}

	public IEnvironmentVariable[] getVariables(IConfiguration cfg, boolean resolveMacros){
		return getVariables(cfg, resolveMacros, true);
	}

	public IEnvironmentVariable[] getVariables(IConfiguration cfg, boolean resolveMacros, boolean checkBuildPaths){
		ICConfigurationDescription des = ManagedBuildManager.getDescriptionForConfiguration(cfg);
		if(des != null){
			IEnvironmentVariable vars[] = fMngr.getVariables(des, resolveMacros);
			if(checkBuildPaths && resolveMacros && fBuildPathVarCheckAllowed)
				checkBuildPathVariables(cfg,vars);
			return vars;
		}
		return new IBuildEnvironmentVariable[0];
	}
	
	public static IBuildEnvironmentVariable wrap(IEnvironmentVariable var){
		if(var == null)
			return null;
		if(var instanceof IBuildEnvironmentVariable)
			return (IBuildEnvironmentVariable)var;
		return new BuildEnvVar(var);
	}

	public static IBuildEnvironmentVariable[] wrap(IEnvironmentVariable vars[]){
		if(vars == null)
			return null;
		if(vars instanceof IBuildEnvironmentVariable[])
			return (IBuildEnvironmentVariable[])vars;
		
		IBuildEnvironmentVariable[] buildVars = new IBuildEnvironmentVariable[vars.length];
		for(int i = 0; i < vars.length; i++){
			buildVars[i] = wrap(vars[i]);
		}
		return buildVars;
	}

/*	protected ICConfigurationDescription getDescription(IConfiguration cfg){
		IProject project = cfg.getOwner().getProject();
		ICProjectDescription des = CoreModel.getDefault().getProjectDescription(project, false);
		if(des != null){
			return des.getConfigurationById(cfg.getId());
		}
		return null;
	}
*/	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#getVariables()
	 */
	public IBuildEnvironmentVariable[] getVariables(Object level,
			boolean includeParentLevels, boolean resolveMacros) {

		if(level instanceof IConfiguration){
			return wrap(getVariables((IConfiguration)level, resolveMacros));
		}
		return new IBuildEnvironmentVariable[0];
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#getDefaultDelimiter()
	 */
	public String getDefaultDelimiter() {
		return fMngr.getDefaultDelimiter();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#isVariableCaseSensitive()
	 */
	public boolean isVariableCaseSensitive() {
		return fMngr.isVariableCaseSensitive();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#getSuppliers()
	 */
	public IEnvironmentVariableSupplier[] getSuppliers(Object level) {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#getBuildPaths()
	 */
	public String[] getBuildPaths(IConfiguration configuration,
			int buildPathType) {
		ITool tools[] = configuration.getFilteredTools();
		List<String> list = new ArrayList<String>();
		
		for(int i = 0; i < tools.length; i++){
			IEnvVarBuildPath pathDescriptors[] = tools[i].getEnvVarBuildPaths();
			
			if(pathDescriptors == null || pathDescriptors.length == 0)
				continue;
			
			for(int j = 0; j < pathDescriptors.length; j++){
				IEnvVarBuildPath curPathDes = pathDescriptors[j];
				if(curPathDes.getType() != buildPathType)
					continue;
				
				String vars[] = curPathDes.getVariableNames();
				if(vars == null || vars.length == 0)
					continue;
				
				IBuildPathResolver pathResolver = curPathDes.getBuildPathResolver();
				if(pathResolver == null){
					String delimiter = curPathDes.getPathDelimiter();
					if(delimiter == null)
						delimiter = getDefaultDelimiter();
					pathResolver = new DefaultBuildPathResolver(delimiter);
				}
				
				for(int k = 0; k < vars.length; k++){
					String varName = vars[k];
					
					IEnvironmentVariable var = getVariable(varName,configuration,true, false);
					if(var == null)
						continue;
					
					String varValue = var.getValue();
					String paths[] = pathResolver.resolveBuildPaths(buildPathType,varName,varValue,configuration);
					if(paths != null && paths.length != 0)
						list.addAll(Arrays.asList(paths));
				}
			}
		}
		
		return list.toArray(new String[list.size()]);
	}
	
	/*
	 * returns a list of registered listeners
	 */
	private List<IEnvironmentBuildPathsChangeListener> getListeners(){
		if(fListeners == null)
			fListeners = new ArrayList<IEnvironmentBuildPathsChangeListener>();
		return fListeners;
	}
	
	/*
	 * notifies registered listeners
	 */
	private void notifyListeners(IConfiguration configuration, int buildPathType){
		List<IEnvironmentBuildPathsChangeListener> listeners = getListeners();
		for (IEnvironmentBuildPathsChangeListener listener : listeners) {
			listener.buildPathsChanged(configuration,buildPathType);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#subscribe()
	 */
	public synchronized void subscribe(IEnvironmentBuildPathsChangeListener listener) {
		if(listener == null)
			return;
		
		List<IEnvironmentBuildPathsChangeListener> listeners = getListeners();
		
		if(!listeners.contains(listener))
			listeners.add(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider#unsubscribe()
	 */
	public synchronized void unsubscribe(IEnvironmentBuildPathsChangeListener listener) {
		if(listener == null)
			return;
		
		List<IEnvironmentBuildPathsChangeListener> listeners = getListeners();
		
		listeners.remove(listener);
	}
	
	/*
	 * performs a check of the build path variables for the given configuration
	 * If the build variables are changed, the notification is sent
	 */
	public void checkBuildPathVariables(IConfiguration configuration){
		checkBuildPathVariables(configuration, getVariables(configuration ,true, false));
	}

	/*
	 * performs a check of the build path variables of the specified type
	 * for the given configuration
	 * If the build variables are changed, the notification is sent
	 */
	public void checkBuildPathVariables(IConfiguration configuration,int buildPathType){
		EnvVarCollector cr = new EnvVarCollector();
		cr.add(getVariables(configuration, true, false));
		checkBuildPathVariables(configuration,buildPathType,cr);
	}

	/*
	 * performs a check of the build path variables
	 * for the given configuration given the set of the variables 
	 * defined for this configuration
	 * If the build variables are changed, the notification is sent
	 */
	protected void checkBuildPathVariables(IConfiguration configuration, IEnvironmentVariable vars[]){
		EnvVarCollector cr = new EnvVarCollector();
		cr.add(vars);
		checkBuildPathVariables(configuration,IEnvVarBuildPath.BUILDPATH_INCLUDE,cr);
		checkBuildPathVariables(configuration,IEnvVarBuildPath.BUILDPATH_LIBRARY,cr);
	}

	/*
	 * performs a check of whether the given variable is the build path variable
	 * and if true checks whether it is changed.
	 * In the case of it is changed all other build path variables are checked
	 * and notification is sent.
	 * If it is not changed, other build path variables are not checked
	 * In the case of the given variable is not the build path one, this method does nothing
	 */
	protected void checkBuildPathVariable(IConfiguration configuration, String varName, IEnvironmentVariable var){
		checkBuildPathVariable(configuration, IEnvVarBuildPath.BUILDPATH_INCLUDE, varName, var);
		checkBuildPathVariable(configuration, IEnvVarBuildPath.BUILDPATH_LIBRARY, varName, var);
	}

	/*
	 * performs a check of whether the given variable is the build path variable
	 * of the specified type and if true checks whether it is changed.
	 * In the case of it is changed all other build path variables of that type are checked
	 * and notification is sent.
	 * If it is not changed, other build path variables are not checked
	 * In the case of the given variable is not the build path one, this method does nothing
	 */
	protected void checkBuildPathVariable(IConfiguration configuration, int buildPathType, String varName, IEnvironmentVariable var){
		StoredBuildPathEnvironmentContainer buildPathVars = getStoredBuildPathVariables(buildPathType);
		if(buildPathVars == null)
			return;
		if(buildPathVars.isVariableChanged(varName,var,configuration)){
			EnvVarCollector cr = new EnvVarCollector();
			cr.add(getVariables(configuration, true, false));
			buildPathVars.synchronize(cr,configuration);
			notifyListeners(configuration, buildPathType);
		}
	}

	/*
	 * performs a check of the build path variables of the specified type
	 * for the given configuration given the set of the variables 
	 * defined for this configuration. 
	 * If the build variables are changed, the notification is sent
	 */
	protected void checkBuildPathVariables(IConfiguration configuration, int buildPathType, EnvVarCollector varSet){
		StoredBuildPathEnvironmentContainer buildPathVars = getStoredBuildPathVariables(buildPathType);
		if(buildPathVars == null)
			return;
		if(buildPathVars.checkBuildPathChange(varSet,configuration)){
			notifyListeners(configuration, buildPathType);
		}
	}
	
	/*
	 * returns the container of the build variables of the specified type
	 */
	protected StoredBuildPathEnvironmentContainer getStoredBuildPathVariables(int buildPathType){
		return buildPathType == IEnvVarBuildPath.BUILDPATH_LIBRARY ?
				getStoredLibraryBuildPathVariables() :
				getStoredIncludeBuildPathVariables();
	}
	
	/*
	 * returns the container of the Include path variables
	 */
	protected StoredBuildPathEnvironmentContainer getStoredIncludeBuildPathVariables(){
		if(fIncludeStoredBuildPathVariables == null)
			fIncludeStoredBuildPathVariables = new StoredBuildPathEnvironmentContainer(IEnvVarBuildPath.BUILDPATH_INCLUDE);
		return fIncludeStoredBuildPathVariables;
	}

	/*
	 * returns the container of the Library path variables
	 */
	protected StoredBuildPathEnvironmentContainer getStoredLibraryBuildPathVariables(){
		if(fLibraryStoredBuildPathVariables == null)
			fLibraryStoredBuildPathVariables = new StoredBuildPathEnvironmentContainer(IEnvVarBuildPath.BUILDPATH_LIBRARY);
		return fLibraryStoredBuildPathVariables;
	}	
}
