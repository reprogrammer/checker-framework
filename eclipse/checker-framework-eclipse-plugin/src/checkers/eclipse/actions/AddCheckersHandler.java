package checkers.eclipse.actions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.statushandlers.StatusManager;

import checkers.eclipse.error.CheckerErrorStatus;
import checkers.eclipse.javac.CommandlineJavacRunner;

public class AddCheckersHandler extends CheckerHandler
{
    private static final String CHECKERS_QUALS_LOCATION = "lib/checkers-quals.jar";

    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        ISelection selection = getSelection(event);
        IJavaElement element = element(selection);
        if (element instanceof IJavaProject)
        {
            IJavaProject javaProject = (IJavaProject) element;
            IProject project = javaProject.getProject();
            IFile jarFile = project.getFile("checkers-quals.jar");

            if (!jarFile.exists())
            {
                FileInputStream input;
                try
                {
                    final String checkerQuals =
                        CommandlineJavacRunner.locatePluginFile(CHECKERS_QUALS_LOCATION).getAbsolutePath();
                    input = new FileInputStream(checkerQuals);
                    jarFile.create(input, false, null);
                } catch (FileNotFoundException e) {
                    StatusManager manager = StatusManager.getManager();
                    CheckerErrorStatus status = new CheckerErrorStatus(
                            "Could not find plugin checkers file.");
                    manager.handle(status, StatusManager.SHOW);
                    return null;
                }catch (CoreException e)
                {
                    StatusManager manager = StatusManager.getManager();
                    CheckerErrorStatus status = new CheckerErrorStatus(
                            "Could not create checkers file in project.");
                    manager.handle(status, StatusManager.SHOW);
                    return null;
                }

                try
                {
                    IClasspathEntry[] entries = javaProject.getRawClasspath();
                    IClasspathEntry[] newEntries = new IClasspathEntry[entries.length + 1];

                    System.arraycopy(entries, 0, newEntries, 0, entries.length);
                    IClasspathEntry javaEntry = JavaCore.newLibraryEntry(
                            jarFile.getLocation(), null, null);
                    newEntries[entries.length] = javaEntry;

                    javaProject.setRawClasspath(newEntries, null);
                }catch (JavaModelException e)
                {
                    StatusManager manager = StatusManager.getManager();
                    CheckerErrorStatus status = new CheckerErrorStatus(
                            "Could not add checkers library to project classpath.");
                    manager.handle(status, StatusManager.SHOW);
                    return null;
                }
            }
            else
            {
                StatusManager manager = StatusManager.getManager();
                CheckerErrorStatus status = new CheckerErrorStatus(
                        "Checkers library already found in project.");
                manager.handle(status, StatusManager.SHOW);
            }
        }

        return null;
    }
}
