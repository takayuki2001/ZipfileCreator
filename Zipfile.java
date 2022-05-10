/**
 * Zipfile？を作るプログラム
 * @author 戸谷　汐里、板橋　賢志
 * @version 1.0.0
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.CRC32;

public class Zipfile {

	/**
	 * 第1引数下層のファイル・ディレクトリを取得し第二引数へ帰す
	 * @param file
	 * @param addfiles
	 */
	static void getAllFile(File file, ArrayList<File> addfiles) {

		//file配下のフォルダ・ディレクトリを取得
		for (File f : file.listFiles()) {
			addfiles.add(f);

			//ディレクトリであれば再帰
			if (f.isDirectory())
				getAllFile(f, addfiles);
		}
	}

	public static void main(String args[]) throws IOException {

		//引数がなければエラー
		if(args.length <= 0)
			System.exit(-1);

		//圧縮対象のファイルリスト
		ArrayList<File> compressTargetFiles = new ArrayList<File>();

		//指定フォルダを取得・下層も取得
		File rootFile = new File(args[0]);
		getAllFile(rootFile, compressTargetFiles);

		//ZipのFileObjを作成
		File zippFile = new File(rootFile.getName() + ".zip");

		//書き込み用にオープン（クローズ兼用）
		try (ReversibleFileOutputStream rfos = new ReversibleFileOutputStream(zippFile)) {

			//あとで行うCentralDirectoryHeader作成時、各種LocalFileHeaderが必要なので保存する
			ArrayList<LocalFileHeader> lfhs = new ArrayList<LocalFileHeader>();

			for (File f : compressTargetFiles) {

				//ファイルであれば内容を読み込み、さもなくば0バイトで初期化
				byte[] bytes;
				if (f.isFile()) {
					bytes = new byte[(int) f.length()];
					try (FileInputStream fis = new FileInputStream(f)) {
						fis.read(bytes, 0, bytes.length);
					}
				} else {
					bytes = new byte[0];
				}

				//crc32を計算
				CRC32 crc = new CRC32();
				crc.update(bytes);

				//LocalFileHeaderを作成
				LocalFileHeader lfh = new LocalFileHeader(
						(short) 10, (short) 0, (short) 0, (short) 0, (short) 0,
						(int) crc.getValue(), bytes.length, bytes.length,
						//圧縮対象からルートパスを引いて相対パス化
						(short) (rootFile.toURI().relativize(f.toURI()).toString()).length(), (short) 0,
						rootFile.toURI().relativize(f.toURI()).toString().getBytes(), new byte[0]);

				//書き込み・List追加
				lfh.write(rfos);
				lfhs.add(lfh);

				//ファイルの中身を書き込み（ディレクトリであれば0）
				FileData fd = new FileData(bytes);
				fd.write(rfos);

			}

			//あとで行うEndOfCentralDirectoryRecord作成時、総個数と総サイズが必要なので用意
			ArrayList<CentralDirectoryHeader> cdhs = new ArrayList<CentralDirectoryHeader>();
			int cdhsSize = 0;

			//LocalFileHeader情報を読み込み・書き込み・List追加にサイズ加算
			for (LocalFileHeader lfh : lfhs) {
				CentralDirectoryHeader cdh = new CentralDirectoryHeader(
						(byte) 3F,
						(short) 10, (short) 0, (short) 0, (short) 0, (short) 0,
						lfh.getCRC32(), lfh.getCompressedSize(), lfh.getUncompressedSize(),
						lfh.getFileNameLength(), (short) 0, (short) 0, (short) 0, (short) 0,
						0, lfh.getPointer(),
						lfh.getFileName(), new byte[0], new byte[0]);
				cdh.write(rfos);
				cdhs.add(cdh);
				cdhsSize += cdh.getSize();
			}

			//EndOfCentralDirectoryRecordを作成・書き込み
			EndOfCentralDirectoryRecord end = new EndOfCentralDirectoryRecord(
					(short) 0, (short) 0, (short) cdhs.size(), (short) cdhs.size(), cdhsSize, cdhs.get(0).getPointer(),
					new byte[0]);
			end.write(rfos);

		} catch (IOException ioe) {

		}
	}
}

class LocalFileHeader {
	int SIGNATURE = 0x04034B50;
	short versionNeededExtract;
	short generalPurposeBitFlag;
	short compressionMethod;
	short lastModFileTime;
	short lastModFileDate;
	int crc32;
	int compressedSize;
	int uncompressedSize;
	short fileNameLength;
	short extraFieldLength;
	byte[] fileName;
	byte[] extraField;

	public int getCRC32() {
		return crc32;
	}

	public int getCompressedSize() {
		return compressedSize;
	}

	public int getUncompressedSize() {
		return uncompressedSize;
	}

	public short getFileNameLength() {
		return fileNameLength;
	}

	public byte[] getFileName() {
		return fileName;
	}

	int pointer = 0;

	public int getPointer() {
		return pointer;
	}

	public LocalFileHeader(
			short versionNeededExtract,
			short generalPurposeBitFlag,
			short compressionMethod,
			short lastModFileTime,
			short lastModFileDate,
			int crc32,
			int compressedSize,
			int uncompressedSize,
			short fileNameLength,
			short extraFieldLength,
			byte[] fileName,
			byte[] extraField) {
		this.versionNeededExtract = versionNeededExtract;
		this.generalPurposeBitFlag = generalPurposeBitFlag;
		this.compressionMethod = compressionMethod;
		this.lastModFileTime = lastModFileTime;
		this.lastModFileDate = lastModFileDate;
		this.crc32 = crc32;
		this.compressedSize = compressedSize;
		this.uncompressedSize = uncompressedSize;
		this.fileNameLength = fileNameLength;
		this.extraFieldLength = extraFieldLength;
		this.fileName = fileName;
		this.extraField = extraField;
	}

	/**
	 * Class内容をすべて書き込む
	 * @param rfos
	 * @throws IOException
	 */
	public void write(ReversibleFileOutputStream rfos) throws IOException {
		//書き込み前にポインタを保存
		pointer = rfos.getPointer();

		rfos.reverseWrite(SIGNATURE);
		rfos.reverseWrite(versionNeededExtract);
		rfos.reverseWrite(generalPurposeBitFlag);
		rfos.reverseWrite(compressionMethod);
		rfos.reverseWrite(lastModFileTime);
		rfos.reverseWrite(lastModFileDate);
		rfos.reverseWrite(crc32);
		rfos.reverseWrite(compressedSize);
		rfos.reverseWrite(uncompressedSize);
		rfos.reverseWrite(fileNameLength);
		rfos.reverseWrite(extraFieldLength);
		rfos.write(fileName);
		rfos.write(extraField);
	}
}

class FileData {
	byte[] data;

	public FileData(byte[] data) {
		this.data = data;
	}

	/**
	 * Class内容をすべて書き込む
	 * @param rfos
	 * @throws IOException
	 */
	public void write(ReversibleFileOutputStream rfos) throws IOException {
		rfos.write(data);
	}
}

class CentralDirectoryHeader {
	int SIGNATURE = 0x02014B50;
	byte versionMadeBy;
	short versionNeededExtract;
	short generalPurposeBitFlag;
	short compressionMethod;
	short lastModFileTime;
	short lastModFileDate;
	int crc32;
	int compressedSize;
	int uncompressedSize;
	short fileNameLength;
	short extraFieldLength;
	short fileCommentLength;
	short diskNumberStart;
	short internalFileAttributes;
	int externalFileAttributes;
	int relativeOffsetLocalHeader;
	byte[] fileName;
	byte[] extraField;
	byte[] fileComment;

	int pointer = 0;

	public int getPointer() {
		return pointer;
	}

	int size = 0;

	/**
	 * Classの総サイズを返答
	 * @return
	 */
	public int getSize() {
		size = Integer.SIZE * 6
				+ Byte.SIZE * 1
				+ Short.SIZE * 10
				+ fileName.length
				+ extraField.length
				+ fileComment.length;

		return size;
	}

	public CentralDirectoryHeader(
			byte versionMadeBy,
			short versionNeededExtract,
			short generalPurposeBitFlag,
			short compressionMethod,
			short lastModFileTime,
			short lastModFileDate,
			int crc32,
			int compressedSize,
			int uncompressedSize,
			short fileNameLength,
			short extraFieldLength,
			short fileCommentLength,
			short diskNumberStart,
			short internalFileAttributes,
			int externalFileAttributes,
			int relativeOffsetLocalHeader,
			byte[] fileName,
			byte[] extraField,
			byte[] fileComment) {
		this.versionMadeBy = versionMadeBy;
		this.versionNeededExtract = versionNeededExtract;
		this.generalPurposeBitFlag = generalPurposeBitFlag;
		this.compressionMethod = compressionMethod;
		this.lastModFileTime = lastModFileTime;
		this.lastModFileDate = lastModFileDate;
		this.crc32 = crc32;
		this.compressedSize = compressedSize;
		this.uncompressedSize = uncompressedSize;
		this.fileNameLength = fileNameLength;
		this.extraFieldLength = extraFieldLength;
		this.fileCommentLength = fileCommentLength;
		this.diskNumberStart = diskNumberStart;
		this.internalFileAttributes = internalFileAttributes;
		this.externalFileAttributes = externalFileAttributes;
		this.relativeOffsetLocalHeader = relativeOffsetLocalHeader;
		this.fileName = fileName;
		this.extraField = extraField;
		this.fileComment = fileComment;
	}

	/**
	 * Class内容をすべて書き込む
	 * @param rfos
	 * @throws IOException
	 */
	public void write(ReversibleFileOutputStream rfos) throws IOException {
		//書き込み前にポインタを保存
		pointer = rfos.getPointer();

		rfos.reverseWrite(SIGNATURE);
		rfos.reverseWrite(versionMadeBy);
		rfos.reverseWrite(versionNeededExtract);
		rfos.reverseWrite(generalPurposeBitFlag);
		rfos.reverseWrite(compressionMethod);
		rfos.reverseWrite(lastModFileTime);
		rfos.reverseWrite(lastModFileDate);
		rfos.reverseWrite(crc32);
		rfos.reverseWrite(compressedSize);
		rfos.reverseWrite(uncompressedSize);
		rfos.reverseWrite(fileNameLength);
		rfos.reverseWrite(extraFieldLength);
		rfos.reverseWrite(fileCommentLength);
		rfos.reverseWrite(diskNumberStart);
		rfos.reverseWrite(internalFileAttributes);
		rfos.reverseWrite(externalFileAttributes);
		rfos.reverseWrite(relativeOffsetLocalHeader);
		rfos.write(fileName);
		rfos.write(extraField);
		rfos.write(fileComment);
	}
}

class EndOfCentralDirectoryRecord {
	int SIGNATURE = 0x06054b50;
	short numberOfThisDisk;
	short numberOfTheDiskWithTheStartOfTheCentralDir;
	short totalNumberInTheCentralDirOnThisDisk;
	short totalNumberInTheCentralDir;
	int sizeOfTheCentralDir;
	int offsetOfStartOfCentralDir;
	short zipCommentLength;
	byte[] zipComment;

	public EndOfCentralDirectoryRecord(
			short numberOfThisDisk,
			short numberOfTheDiskWithTheStartOfTheCentralDir,
			short totalNumberInTheCentralDirOnThisDisk,
			short totalNumberInTheCentralDir,
			int sizeOfTheCentralDir,
			int offsetOfStartOfCentralDir,
			byte[] zipComment) {
		this.numberOfThisDisk = numberOfThisDisk;
		this.numberOfTheDiskWithTheStartOfTheCentralDir = numberOfTheDiskWithTheStartOfTheCentralDir;
		this.totalNumberInTheCentralDirOnThisDisk = totalNumberInTheCentralDirOnThisDisk;
		this.totalNumberInTheCentralDir = totalNumberInTheCentralDir;
		this.sizeOfTheCentralDir = sizeOfTheCentralDir;
		this.offsetOfStartOfCentralDir = offsetOfStartOfCentralDir;
		this.zipCommentLength = (short) zipComment.length;
		this.zipComment = zipComment;
	}

	/**
	 * Class内容をすべて書き込む
	 * @param rfos
	 * @throws IOException
	 */
	public void write(ReversibleFileOutputStream rfos) throws IOException {
		//書き込み前にポインタを保存
		rfos.reverseWrite(SIGNATURE);
		rfos.reverseWrite(numberOfThisDisk);
		rfos.reverseWrite(numberOfTheDiskWithTheStartOfTheCentralDir);
		rfos.reverseWrite(totalNumberInTheCentralDirOnThisDisk);
		rfos.reverseWrite(totalNumberInTheCentralDir);
		rfos.reverseWrite(sizeOfTheCentralDir);
		rfos.reverseWrite(offsetOfStartOfCentralDir);
		rfos.reverseWrite(zipCommentLength);
		rfos.write(zipComment);
	}
}

class ReversibleFileOutputStream extends FileOutputStream {

	//仮想ファイルポインタ
	private int pFile = 0;

	public int getPointer() {
		return pFile;
	}

	/**
	 * 書き込み対象のFileObjを要求し、初期化する
	 * @param file
	 * @throws FileNotFoundException
	 */
	public ReversibleFileOutputStream(File file) throws FileNotFoundException {
		super(file);
	}

	/**
	 * リトルエンディアンで書き込み
	 * @param datas
	 * @throws IOException
	 */
	public void reverseWrite(byte[] datas) throws IOException {

		//反転
		byte[] rDatas = new byte[datas.length];
		for (int i = 0; i < datas.length; i++) {
			rDatas[i] = datas[datas.length - 1 - i];
		}

		//書き込み
		write(rDatas);
	}

	/**
	 * リトルエンディアンで書き込み
	 * @param n
	 * @throws IOException
	 */
	public void reverseWrite(int n) throws IOException {
		//byte[]化
		byte[] datas = (ByteBuffer.allocate(Integer.BYTES).putInt(n).array());
		reverseWrite(datas);
	}

	/**
	 * リトルエンディアンで書き込み
	 * @param m
	 * @throws IOException
	 */
	public void reverseWrite(short m) throws IOException {
		//byte[]化
		byte[] datas = (ByteBuffer.allocate(Short.BYTES).putShort(m).array());
		reverseWrite(datas);
	}

	/**
	 * 書き込み
	 * @param b
	 * @throws IOException
	 */
	@Override
	public void write(byte[] b) throws IOException {
		//ポインタを加算
		pFile += b.length;
		super.write(b);
	}

	/**
	 * 書き込み
	 * @param b
	 * @param off
	 * @param len
	 * @throws IOException
	 */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		//ポインタを加算
		pFile += len;
		super.write(b, off, len);
	}

	/**
	 * 書き込み
	 * @param b
	 * @throws IOException
	 */
	@Override
	public void write(int b) throws IOException {
		//ポインタを加算
		pFile += Integer.SIZE;
		super.write(b);
	}
}
