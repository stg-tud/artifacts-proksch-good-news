/*******************************************************************************
 * Copyright (c) 2011 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sebastian Proksch - initial API and implementation
 ******************************************************************************/
package cc.recommenders.io;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import cc.recommenders.exceptions.AssertionException;

public class DirectoryTest {

	private String tempFileName;

	private Directory uut;

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Before
	public void setup() {
		tempFileName = tempFolder.getRoot().getAbsolutePath();
		uut = new Directory(tempFileName + "/");
	}

	@Test
	public void InitAddsTrialingSlash() throws IOException {
		String withSlash = tempFileName + "/with/";
		URL a = new Directory(withSlash).getUrl();
		assertTrue(a.toString().endsWith("/"));

		String withoutSlash = tempFileName + "/without";
		URL b = new Directory(withoutSlash).getUrl();
		assertTrue(b.toString().endsWith("/"));
	}

	@Test
	public void objectWritesCreateNewFiles() throws IOException {
		setup();
		uut.write("blubb", "file.txt");
		assertFileExists("file.txt");
	}

	@Test
	public void serializationRoundtripWorks() throws IOException {
		setup();

		String expected = "someContent";
		uut.write(expected, "somefile.txt");
		String actual = uut.read("somefile.txt", String.class);

		assertNotSame(expected, actual);
		assertEquals(expected, actual);
	}

	@Test
	public void existingFilesAreFound() throws IOException {
		setup();
		String fileName = "afile";
		tempFolder.newFile(fileName);
		assertTrue(uut.exists(fileName));
	}

	@Test
	public void notExistingFilesAreDetected() throws IOException {
		setup();
		String fileName = "aNonExistingFile";
		assertFalse(uut.exists(fileName));
	}

	@Test
	public void ifNoFileExistsTheCountIsZero() {
		setup();
		int count = uut.count();
		int expected = 0;
		assertEquals(expected, count);
	}

	@Test
	public void ifOneFileExistsTheCountIsOne() throws IOException {
		setup();
		tempFolder.newFile("firstFile");
		int count = uut.count();
		int expected = 1;
		assertEquals(expected, count);
	}

	@Test
	public void ifAFolderExistsItIsCountedWithoutChilds() throws IOException {
		setup();
		tempFolder.newFolder("afolder");
		tempFolder.newFile("afolder/file");
		tempFolder.newFile("afolder/file2");

		int count = uut.count();
		int expected = 1;
		assertEquals(expected, count);
	}

	@Test
	public void directoryCanBeCleared() throws IOException {
		setup();
		tempFolder.newFile("someFile");
		tempFolder.newFolder("afolder");
		tempFolder.newFile("afolder/file");
		tempFolder.newFile("afolder/file2");

		uut.clear();

		int actual = tempFolder.getRoot().list().length;
		int expected = 0;
		assertEquals(expected, actual);
	}

	@Test
	public void filesCanBeDeleted() throws IOException {
		setup();
		String fileName = "someFile";
		tempFolder.newFile(fileName);
		uut.delete(fileName);
		assertFalse(uut.exists(fileName));
	}

	@Test
	public void listingContainsAllFiles() throws IOException {
		setup();

		Set<String> expected = new HashSet<String>();

		for (String file : new String[] { "fileA", "fileB", "fileC" }) {
			tempFolder.newFile(file);
			expected.add(file);
		}

		assertEquals(expected, uut.list());
	}

	@Test
	public void validUrlsAreCreatedForTheDirectory() throws IOException {
		setup();

		URL expected = new URL("file://" + tempFileName + "/");
		URL actual = uut.getUrl();

		assertEquals(expected, actual);
	}

	@Test
	public void validUrlsAreCreatedForContainingFiles() throws IOException {
		setup();

		File newFile = tempFolder.newFile("test");
		URL expected = new URL("file://" + newFile.getAbsolutePath());

		URL actual = uut.getUrl("test");

		assertEquals(expected, actual);
	}

	@Test
	public void foldersCanBeCreated() {
		setup();
		uut.createDirectory("subdir");
		File folder = new File(tempFileName + "/subdir");

		assertTrue(folder.exists());
		assertTrue(folder.isDirectory());
	}

	@Test
	public void foldersWithSubfoldersCanBeCreated() {
		setup();
		uut.createDirectory("subdir/subsubdir");
		File folder = new File(tempFileName + "/subdir/subsubdir");

		assertTrue(folder.exists());
		assertTrue(folder.isDirectory());
	}

	@Test
	public void creatingFoldersGetsTheNewFolderReturned() throws MalformedURLException {
		setup();
		Directory blubbDir = uut.createDirectory("blubb");

		URL expected = new URL("file://" + tempFileName + "/blubb/");
		URL actual = blubbDir.getUrl();

		assertEquals(expected, actual);
	}

	@Test
	public void writeReadContentRoundtrip() throws IOException {
		setup();
		String expected = "This is some string, that should be written and read";
		String fileName = "roundtrip.txt";
		uut.writeContent(expected, fileName);
		String actual = uut.readContent(fileName);
		assertEquals(expected, actual);
	}

	@Test
	public void writeReadArchiveRoundtrip() throws IOException {
		setup();

		String fileName = "archive.zip";
		WritingArchive writingArchive = uut.getWritingArchive(fileName);

		Set<String> expected = new HashSet<String>();
		Set<String> actual = new HashSet<String>();

		for (String content : new String[] { "first string", "a second string for serializing", "last one" }) {
			writingArchive.add(content);
			expected.add(content);
		}
		writingArchive.close();

		ReadingArchive readingArchive = uut.getReadingArchive(fileName);

		assertEquals(3, readingArchive.numberOfEntries());

		while (readingArchive.hasNext()) {
			String s = readingArchive.getNext(String.class);
			actual.add(s);
		}
		readingArchive.close();

		assertEquals(expected, actual);
	}

	@Test
	public void writeReadArchivePlainRoundtrip() throws IOException {
		setup();

		String fileName = "archive.zip";
		WritingArchive writingArchive = uut.getWritingArchive(fileName);

		Set<String> expected = new HashSet<String>();
		Set<String> actual = new HashSet<String>();

		writingArchive.addPlain("xyz", "x.txt");
		expected.add("xyz");

		for (String content : new String[] { "fir<st st>ring", "a second string for serializing", "last one" }) {
			writingArchive.addPlain(content);
			expected.add(content);
		}
		writingArchive.close();

		ReadingArchive readingArchive = uut.getReadingArchive(fileName);

		assertEquals(4, readingArchive.numberOfEntries());

		while (readingArchive.hasNext()) {
			String s = readingArchive.getNextPlain();
			actual.add(s);
		}
		readingArchive.close();

		assertEquals(expected, actual);
	}

	@Test
	public void getWritingArchiveCreateParentFolder() throws IOException {
		uut.getWritingArchive("a/b.zip");
		File expected = new File(tempFileName, "a/b.zip");
		assertTrue(expected.exists());
	}

	@Test
	public void writeReopenReadRoundtrip() throws IOException {
		setup();

		WritingArchive archive = uut.getWritingArchive("test.zip");
		archive.add("eins");
		archive.close();

		archive = uut.reopenWritingArchive("test.zip", String.class);
		archive.add("zwei");
		archive.close();

		List<String> actual = Lists.newArrayList();
		ReadingArchive readingArchive = uut.getReadingArchive("test.zip");
		while (readingArchive.hasNext()) {
			String next = readingArchive.getNext(String.class);
			actual.add(next);
		}

		List<String> expected = Lists.newArrayList("eins", "zwei");
		assertEquals(expected, actual);

		assertEquals(newHashSet("test.zip"), uut.list());
	}

	@Test
	public void generatedFileNamesDoNOtContainBadChars() {
		String input = "azAZ09.-_;,:?!\"�$%&/()=?";
		String expec = "azAZ09.-__";
		String actual = Directory.createFileName(input);
		assertEquals(expec, actual);
	}

	@Test
	public void listCanBeFilteredByPredicate() throws IOException {
		setup();
		uut.write("a", "a.jpg");
		uut.write("b", "b.zip");
		uut.write("c", "c.png");
		uut.write("d", "d.zip");
		uut.write("e", "e.pdf");

		Set<String> actual = uut.list(new Predicate<String>() {
			@Override
			public boolean apply(String name) {
				return name.endsWith(".zip");
			}
		});

		Set<String> expected = newHashSet("b.zip", "d.zip");

		assertEquals(expected, actual);
	}

	@Test
	public void findFiles() throws IOException {

		uut = new Directory(tempFileName);

		new File(tempFileName + "/1.txt").createNewFile();
		new File(tempFileName + "/2/2").mkdirs();
		new File(tempFileName + "/2/2.txt").createNewFile();
		new File(tempFileName + "/3/3").mkdirs();
		new File(tempFileName + "/3/3/3.txt").createNewFile();

		Set<String> actuals = uut.findFiles(f -> f.endsWith(".txt"));
		Set<String> expecteds = Sets.newHashSet("1.txt", "2/2.txt", "3/3/3.txt");
		assertEquals(expecteds, actuals);
	}

	public void directoriesAreCreatedOnInit() {
		setup();
		String newFolderName = tempFolder.getRoot().getAbsolutePath() + "/test";
		new Directory(newFolderName);

		File newFolder = new File(newFolderName);
		assertTrue(newFolder.exists());
		assertTrue(newFolder.isDirectory());
	}

	@Test(expected = RuntimeException.class)
	public void creatingFolderOnExistingFile() throws IOException {
		setup();
		tempFolder.newFile("a.txt");
		new Directory(tempFileName + "/a.txt");
	}

	@Test(expected = AssertionException.class)
	public void openingInvalidFolder() {
		File nonExisting = new File("/path/to/!nv4lid:folder?");
		new Directory(nonExisting.getAbsolutePath());
	}

	@Test
	public void findingFilesUsesPredicate() {
		createFileInSubDir("a.zip");
		createFileInSubDir("x.txt");

		Directory sut = new Directory(tempFileName);
		Set<String> actual = sut.findFiles(f -> f.endsWith(".zip"));
		Set<String> expected = Sets.newHashSet("a.zip");
		assertEquals(expected, actual);
	}

	@Test
	public void findingFilesRecursively() {
		createFileInSubDir("a.zip");
		createFileInSubDir("b.zip", "b");
		createFileInSubDir("c.zip", "c", "c");

		Directory sut = new Directory(tempFileName);
		Set<String> actual = sut.findFiles(f -> true);
		Set<String> expected = Sets.newHashSet("a.zip", "b/b.zip", "c/c/c.zip");
		assertEquals(expected, actual);
	}

	@Test(expected = AssertionException.class)
	public void getParent_nonExisting() {
		Directory sut = new Directory(tempFileName);
		sut.getParentDirectory("a/b/c/etc");
	}

	@Test(expected = AssertionException.class)
	public void getParent_tryingToEscape() {
		Directory sut = new Directory(tempFileName);
		sut.getParentDirectory("../");
	}

	@Test
	public void getParent_realCase() throws IOException {
		Directory sut = new Directory(tempFileName);
		sut.createDirectory("a/b/").writeContent("...", "c.txt");

		Directory d = sut.getParentDirectory("a/b/c.txt");

		URL actual = d.getUrl();
		URL expected = new Directory(tempFileName + "/a/b").getUrl();
		assertEquals(expected, actual);
	}

	private void createFileInSubDir(String fileName, String... folders) {
		String folderPath = tempFileName + "/" + String.join("/", folders);
		File folder = new File(folderPath);
		if (!folder.exists()) {
			folder.mkdirs();
		}
		String file = folderPath + "/" + fileName;
		try {
			new File(file).createNewFile();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private void assertFileExists(String fileToCheck) {
		String fileName = tempFileName + "/" + fileToCheck;
		File file = new File(fileName);

		assertTrue(file.exists());
	}
}
