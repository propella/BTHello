-*- outline -*-

* Android で Bluetooth プログラミング。SPP は 21 世紀のシリアル端子。

Bluetooth と言うといかにも旬が過ぎたテクノロジーですが、技術がこなれ多
くの機器に標準で搭載されるようになった今こそ、やっとホビイストにとって
Bluetooth がお手頃に使える時が来たと言えるのでは無いでしょうか？特に
USB と違ってコードが要らず、無線 LAN より設定が簡単なので、ちょっとした
通信には最適だと思います。最近の Android にはだいたい Bluetooth が付い
てるらしいので、Android の Bluetooth を使い、Mac の仮装シリアルポートに
接続する方法を書きます。本当は iPhone でも試したかったのですが、 Apple
は素人が Bluetooth を使う方法を提供していないので諦めました。

** Bluetooth とは何か？

プログラマから見て、Bluetooth はだいたい TCP/IP と同じように扱えます。
TCP/IP と同じくデータグラム型通信である L2CAP の上にセッション型通信の
RFCOMM が乗っていて、RFCOMM をそのままシリアルポートとして使うことが出
来ます。

Bluetooth でもポート番号がありますが、TCP/IP と違いポートは動的に決まり
ます。ポートが動的に決まるならそのポート番号はどうやって見つければ良い
のかというと、本気で接続する前にまず SDP というポート検索サービスを使っ
てサービスとポート番号との対応を調べます。検索キーになるのは Service
ID と呼ばれる UUID で、これは予め決めておきます。という訳で、TCP/IP ク
ライアントは IP アドレスとポート番号を使って接続しますが、Bluetooth は
MAC アドレスと UUID で接続します。

実際には Bluetooth 機器は複数の通信経路を使うことがよくあります。例えば
Bluetooth ヘッドフォンだと、音声の経路とは別に通信品質やエンコーディン
グ情報のやり取りが必要です。このように一本の経路(プロトコル)とは別に、
サビースごとに複数のプロトコルの使い方を定めたものをプロファイルと呼び
ます。例えばヘッドフォンに使う A2DP やワイヤレスマウスに使う HID などが
あります。

プロファイルはインターネット RFC のようなものです。参照関係が沢山あって
読みにくいので、最初は Serial Port Profile のような単純なやつから勉強す
ると良いです。Android の API では RFCOMM しか扱えないので、HID に必要な
L2CAP を使って無線マウスやキーボードを Java で自作する事は出来ないみた
いです。Android-NDK と C で書けばいけるかも。

** Android で Bluetooth を使う準備

ここでは Android をサーバーとして動かし、SPP (Serial Port Profile) 経由
で Mac と接続してみます。Android で Bluetooth を使う方法は
http://developer.android.com/guide/topics/wireless/bluetooth.html に詳
しく書いてありますので、そのまま真似すれば動きます。要点だけ書くと、
android.permission.BLUETOOTH を AndroidManifest.xml 内で
android.permission.BLUETOOTH を有効にする必要があります。

>|xml|
<manifest ... >
  <uses-permission android:name="android.permission.BLUETOOTH" />
  ...
</manifest>
||<

** サーバーとして Bluetooth で待ち受ける

先程 Bluetooth 接続には Service ID が必要だと書きました。ここでは SPP
を使いますので SPP で定められた Service ID を使います。また、Service
Name というのを指定して接続に名前をつけます。Bluetooth デバイスへのアク
セスには BluetoothAdapter を使います。

>|java|
public class BTHelloActivity extends Activity {
    private static final String TAG = "BTHello";
    private static final String SERVICE_NAME = "BTHello";
    private static final String SERIAL_PORT_SERVICE_ID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final UUID SERVICE_ID = UUID.fromString(SERIAL_PORT_SERVICE_ID);
    AcceptThread thread;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
||<

Activity の残りのコードでは、単に起動時に接続スレッドを立ち上げ、停止時に削除します。

>|java|
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	thread = new AcceptThread();
    	thread.start();
    }
    
    @Override
    protected void onDestroy () {
    	if (thread != null) {
    		thread.cancel();
    	}
    }
||<

定義した値を指定してサーバーソケットを作ります。Android API は大まかに
出来ているので listenUsingRfcommWithServiceRecord だけで SDP への登録と
サーバーソケット作成を同時に行います。

>|java|
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
     
        public AcceptThread() {
            try {
            	serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_ID);
            } catch (IOException e) { }
        }
||<

後は TCP サーバと同じです。accept で待ちうけ、やってきた接続で新しいソケットを作り通信部分に渡します。

>|java|
        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Fail to accept.", e);
                    break;
                }
            	Log.d(TAG, "A connection was accepted.");
                if (socket != null) {
                    connect(socket);
                }
            	Log.d(TAG, "The session was closed. Listen again.");
            }
        }
||<

通信部分では単に一文字来るごとに現在時刻を返しています。ここではサボってますが、普通は入力と出力で別スレッドにしたほうが良いと思います。

>|java|
        private void connect(BluetoothSocket socket) {
            
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                
                Log.d(TAG, "Connection established.");
                out.write("Hello I'm Bluetooth! Press Q to quit.\r\n".getBytes());
                while (true) {
                    byte[] buffer = new byte[1024];
                    int bytes = in.read(buffer);
                    Log.d(TAG, "input =" + new String(buffer, 0, bytes));
                    out.write((df.format(new Date()) + ": " + new String (buffer, 0, bytes) + "\r\n").getBytes());
                    if (buffer[0] == 'q') {
                        out.write(("Bye!\r\n").getBytes());
                        break;
                    }
                }
                socket.close();
                
            } catch (IOException e) {
                Log.e(TAG, "Something bad happened!", e);
            }
        }
||<

で、後始末しておしまいです。

>|java|
        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                serverSocket.close();
    	    	Log.d(TAG, "The server socket is closed.");
            } catch (IOException e) { }
        }
    }
}
||<





- Serial Port Profile http://www.palowireless.com/infotooth/tutorial/k5_spp.asp
- A2DP http://developer.bluetooth.org/KnowledgeCenter/TechnologyOverview/Pages/A2DP.aspx
