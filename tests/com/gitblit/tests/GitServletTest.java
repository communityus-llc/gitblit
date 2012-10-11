package com.gitblit.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;

public class GitServletTest {

	static File ticgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");
	
	static File ticgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit2");

	static File jgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/jgit");
	
	static File jgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/jgit2");

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
			deleteWorkingFolders();
		}
	}
	
	public static void deleteWorkingFolders() throws Exception {
		if (ticgitFolder.exists()) {
			GitBlitSuite.close(ticgitFolder);
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE);
		}
		if (ticgit2Folder.exists()) {
			GitBlitSuite.close(ticgit2Folder);
			FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		}
		if (jgitFolder.exists()) {
			GitBlitSuite.close(jgitFolder);
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE);
		}
		if (jgit2Folder.exists()) {
			GitBlitSuite.close(jgit2Folder);
			FileUtils.delete(jgit2Folder, FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testClone() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		GitBlitSuite.close(clone.call());		
		assertTrue(true);
	}

	@Test
	public void testBogusLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = GitBlit.self().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		// delete any existing working folder		
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("bogus", "bogus"));
			GitBlitSuite.close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		assertFalse("Bogus login cloned a repository?!", cloned);
	}
	
	@Test
	public void testUnauthorizedLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = GitBlit.self().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		UserModel user = new UserModel("james");
		user.password = "james";
		GitBlit.self().updateUserModel(user.username, user, true);
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		
		// delete any existing working folder		
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.username, user.password));
			GitBlitSuite.close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		assertFalse("Unauthorized login cloned a repository?!", cloned);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		
		// switch to authenticated
		model.authorizationControl = AuthorizationControl.AUTHENTICATED;
		GitBlit.self().updateRepositoryModel(model.name, model, false);
		
		// try clone again
		cloned = false;
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
		clone.setDirectory(ticgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.username, user.password));
		GitBlitSuite.close(clone.call());
		cloned = true;

		assertTrue("Authenticated login could not clone!", cloned);
		
		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		
		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		GitBlit.self().updateRepositoryModel(model.name, model, false);
		GitBlit.self().deleteUser(user.username);
	}

	@Test
	public void testAnonymousPush() throws Exception {
		Git git = Git.open(ticgitFolder);
		File file = new File(ticgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// hellol中文 " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		git.push().setPushAll().call();
		GitBlitSuite.close(git);
	}

	@Test
	public void testSubfolderPush() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/test/jgit.git", url));
		clone.setDirectory(jgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgitFolder);
		File file = new File(jgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		git.push().setPushAll().call();
		GitBlitSuite.close(git);
	}
	
	@Test
	public void testPushToNonBareRepository() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/working/jgit", url));
		clone.setDirectory(jgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgit2Folder);
		File file = new File(jgit2Folder, "NONBARE");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit followed by push to non-bare repository").call();
		try {
			git.push().setPushAll().call();
			assertTrue(false);
		} catch (Exception e) {
			assertTrue(e.getCause().getMessage().contains("git-receive-pack not permitted"));
		}
		GitBlitSuite.close(git);
	}

	@Test
	public void testBlockClone() throws Exception {
		testRefChange(AccessPermission.VIEW, null, null, null);
	}

	@Test
	public void testBlockPush() throws Exception {
		testRefChange(AccessPermission.CLONE, null, null, null);
	}

	@Test
	public void testBlockBranchCreation() throws Exception {
		testRefChange(AccessPermission.PUSH, Status.REJECTED_OTHER_REASON, null, null);
	}

	@Test
	public void testBlockBranchDeletion() throws Exception {
		testRefChange(AccessPermission.CREATE, Status.OK, Status.REJECTED_OTHER_REASON, null);
	}
	
	@Test
	public void testBlockBranchRewind() throws Exception {
		testRefChange(AccessPermission.DELETE, Status.OK, Status.OK, Status.REJECTED_OTHER_REASON);
	}

	@Test
	public void testBranchRewind() throws Exception {		
		testRefChange(AccessPermission.REWIND, Status.OK, Status.OK, Status.OK);
	}

	private void testRefChange(AccessPermission permission, Status expectedCreate, Status expectedDelete, Status expectedRewind) throws Exception {

		UserModel user = new UserModel("james");
		user.password = "james";
		
		if (GitBlit.self().getUserModel(user.username) != null) {
			GitBlit.self().deleteUser(user.username);
		}
		
		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(user.username, user.password);
		
		// fork from original to a temporary bare repo
		File refChecks = new File(GitBlitSuite.REPOSITORIES, "refchecks/ticgit.git");
		if (refChecks.exists()) {
			FileUtils.delete(refChecks, FileUtils.RECURSIVE);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
		clone.setDirectory(refChecks);
		clone.setBare(true);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		// elevate repository to clone permission
		RepositoryModel model = GitBlit.self().getRepositoryModel("refchecks/ticgit.git");
		switch (permission) {
			case VIEW:
				model.accessRestriction = AccessRestrictionType.CLONE;
				break;
			case CLONE:
				model.accessRestriction = AccessRestrictionType.CLONE;
				break;
			default:
				model.accessRestriction = AccessRestrictionType.PUSH;
		}
		model.authorizationControl = AuthorizationControl.NAMED;
		
		// grant user specified
		user.setRepositoryPermission(model.name, permission);

		GitBlit.self().updateUserModel(user.username, user, true);
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		// clone temp bare repo to working copy
		File local = new File(GitBlitSuite.REPOSITORIES, "refchecks/ticgit-wc");
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE);
		}
		clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/{1}", url, model.name));
		clone.setDirectory(local);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		
		try {
			GitBlitSuite.close(clone.call());
		} catch (GitAPIException e) {
			if (permission.atLeast(AccessPermission.CLONE)) {
				throw e;
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);
				
				// user does not have clone permission
				assertTrue(e.getMessage(), e.getMessage().contains("not permitted"));	
				return;
			}
		}
		
		Git git = Git.open(local);
		
		// commit a file and push it
		File file = new File(local, "PUSHCHK");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("push test").call();
		Iterable<PushResult> results = null;
		try {
			results = git.push().setCredentialsProvider(cp).setRemote("origin").call();
		} catch (GitAPIException e) {
			if (permission.atLeast(AccessPermission.PUSH)) {
				throw e;
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);
				
				// user does not have push permission
				assertTrue(e.getMessage(), e.getMessage().contains("not permitted"));
				GitBlitSuite.close(git);
				return;
			}
		}
		
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (permission.atLeast(AccessPermission.PUSH)) {
				assertTrue("User failed to push commit?! " + status.name(), Status.OK.equals(status));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push commit! " + status.name(), Status.REJECTED_OTHER_REASON.equals(status));
				GitBlitSuite.close(git);
				// skip delete test
				return;
			}
		}
		
		// create a local branch and push the new branch back to the origin				
		git.branchCreate().setName("protectme").call();
		RefSpec refSpec = new RefSpec("refs/heads/protectme:refs/heads/protectme");
		results = git.push().setCredentialsProvider(cp).setRefSpecs(refSpec).setRemote("origin").call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/protectme");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedCreate)) {
				assertTrue("User failed to push creation?! " + status.name(), status.equals(expectedCreate));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push ref creation! " + status.name(), status.equals(expectedCreate));
				GitBlitSuite.close(git);
				// skip delete test
				return;
			}
		}
		
		// delete the branch locally
		git.branchDelete().setBranchNames("protectme").call();
		
		// push a delete ref command
		refSpec = new RefSpec(":refs/heads/protectme");
		results = git.push().setCredentialsProvider(cp).setRefSpecs(refSpec).setRemote("origin").call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/protectme");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedDelete)) {
				assertTrue("User failed to push ref deletion?! " + status.name(), status.equals(Status.OK));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push ref deletion?! " + status.name(), status.equals(expectedDelete));
				GitBlitSuite.close(git);
				// skip rewind test
				return;
			}
		}
		
		// rewind master by two commits
		git.reset().setRef("HEAD~2").setMode(ResetType.HARD).call();
		
		// commit a change on this detached HEAD
		file = new File(local, "REWINDCHK");
		os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		RevCommit commit = git.commit().setMessage("rewind master and new commit").call();
		
		// Reset master to our new commit now we our local branch tip is no longer
		// upstream of the remote branch tip.  It is an alternate tip of the branch.
		JGitUtils.setBranchRef(git.getRepository(), "refs/heads/master", commit.getName());
		
		// Try pushing our new tip to the origin.
		// This requires the server to "rewind" it's master branch and update it
		// to point to our alternate tip.  This leaves the original master tip
		// unreferenced.
		results = git.push().setCredentialsProvider(cp).setRemote("origin").setForce(true).call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedRewind)) {
				assertTrue("User failed to rewind master?! " + status.name(), status.equals(expectedRewind));
			} else {
				assertTrue("User was able to rewind master?! " + status.name(), status.equals(expectedRewind));
			}
		}
		GitBlitSuite.close(git);
		
		// close serving repository
		GitBlitSuite.close(refChecks);

		GitBlit.self().deleteUser(user.username);
	}
}
