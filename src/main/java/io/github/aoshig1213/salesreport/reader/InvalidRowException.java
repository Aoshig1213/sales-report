package io.github.aoshig1213.salesreport.reader;

/** 行（またはファイル）の内容が不正。メッセージはそのままエラーシートに出す */
public class InvalidRowException extends RuntimeException {

    public InvalidRowException(String message) {
        super(message);
    }
}
