-*- outline -*-

* Android で Bluetooth プログラミング。SPP は 21 世紀のシリアル端子。

Bluetooth と言うといかにも旬が過ぎたテクノロジーですが、技術がこなれ多くの機器に標準で搭載されるようになった今こそ、やっとホビイストにとって Bluetooth がお手頃に使える時が来たと言えるのでは無いでしょうか？特に USB と違ってコードが要らず、無線 LAN より設定が簡単なので、ちょっとした通信には最適だと思います。最近の Android にはだいたい Bluetooth が付いてるらしいので、Android の Bluetooth を使い、Mac の仮装シリアルポートに接続する方法を書きます。本当は iPhone でも試したかったのですが、 Apple は素人が Bluetooth を使う方法を提供していないので諦めました。

** Bluetooth とは何か？

プログラマから見て、Bluetooth はだいたい TCP/IP と同じように扱えます。TCP/IP と同じくデータグラム型通信である L2CAP の上にセッション型通信の RFCOMM が乗っていて、RFCOMM をそのままシリアルポートとして使うことが出来ます。

Bluetooth でもポート番号がありますが、TCP/IP と違いポートは動的に決まります。ポートが動的に決まるならそのポート番号はどうやって見つければ良いのかというと、本気で接続する前にまず SDP というポート検索サービスを使ってサービスとポート番号との対応を調べます。検索キーになるのは Service ID と呼ばれる UUID で、これは予め決めておきます。という訳で、TCP/IP クライアントが IP アドレスとポート番号を使って接続するのに対して、Bluetooth は MAC アドレスと UUID で接続を試みます。

実際には Bluetooth 機器は複数の通信経路を使うことがよくあります。例えば Bluetooth ヘッドフォンだと、音声の経路とは別に通信品質やエンコーディング情報のやり取りが必要です。このように一本の経路(プロトコル)とは別に、機能ごとに複数のプロトコルの使い方を定めたものをまとめてプロファイルと呼びます。プロファイルには例えばヘッドフォンに使う A2DP やワイヤレスマウスに使う HID などがあります。

プロファイルはインターネットで言う RFC のようなものです。参照関係が沢山あって読みにくいので、最初は Serial Port Profile のような単純なやつから勉強すると良いです。Android の API では RFCOMM しか扱えないのでどんなプロファイルでも自作出来るわけではないです。例えば無線マウスやキーボードの HID プロファイルには L2CAP が要るので、Java で自作する事は出来ません。多分 Android-NDK と C を使えば作れると思います。

** Android で Bluetooth を使う準備

ここでは Android をサーバーとして動かし、SPP (Serial Port Profile) 経由で Mac と接続してみます。Android で Bluetooth を使う方法は http://developer.android.com/guide/topics/wireless/bluetooth.html に詳しく書いてありますので、そのまま真似すれば動きます。要点だけ書くと、まず AndroidManifest.xml 内で android.permission.BLUETOOTH を有効にする必要があります。

>|xml|
<manifest ... >
  <uses-permission android:name="android.permission.BLUETOOTH" />
  ...
</manifest>
||<

** サーバーとして Bluetooth で待ち受ける

先程 Bluetooth 接続には Service ID が必要だと書きました。ここでは SPP を使いますので SPP で定められた Service ID "00001101-0000-1000-8000-00805F9B34FB" を使います。また、Service Name というのを指定して接続に適当に名前をつけます。Bluetooth デバイスへのアクセスには BluetoothAdapter を使います。

>|java|
public class BTHelloActivity extends Activity {
    private static final String TAG = "BTHello";
    private static final String SERVICE_NAME = "BTHello";
    private static final String SERIAL_PORT_SERVICE_ID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final UUID SERVICE_ID = UUID.fromString(SERIAL_PORT_SERVICE_ID);
    AcceptThread thread;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
||<

Activity の残りのコードでは、単に起動時に接続スレッドを立ち上げ、停止時に削除してるだけです。

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

定義した値を指定してサーバーソケットを作ります。Android API は大まかに出来ているので listenUsingRfcommWithServiceRecord だけで SDP への登録とサーバーソケット作成を同時に行えます。逆に言うと直接ポート番号を指定する事は出来ません。

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

** Mac OSX をクライアントとして使う

Mac OSX は最初から SPP をサポートしていますので、先程作った SPP をシリアルポートとして繋げる事が出来ます。

まず Android 側の設定

- 「設定」-「無線とネットワーク」-「Bluetooth 設定」で Bluetooth の設定画面を開けます
- 「Bluetooth」 のチェックが有効である事を確認します。
- 「検出可能」チェックを有効にします。

Mac 側はちょっとややこしいです。

- システムプリファレンスの Bluetooth を開きます。
- + ボタンを押してしばらく待つと、Android の名前が現れるので選択します。

<a href="http://www.flickr.com/photos/propella/7041977599/" title="bt1 by propella, on Flickr"><img src="http://farm8.staticflickr.com/7109/7041977599_1b346212ee_n.jpg" width="320" height="211" alt="bt1"></a>

こういうペア設定の画面が出るので、Android 側を確認して「ペア設定」を押します。

<a href="http://www.flickr.com/photos/propella/6895880658/" title="bt2 by propella, on Flickr"><img src="http://farm8.staticflickr.com/7097/6895880658_ef980c4a27_n.jpg" width="320" height="211" alt="bt2"></a>

結果ダイアログを閉じると、先ほどの Bluetooth 設定画面に Android が現れていますので選択し、下の小さな歯車ボタンの中の 「Edit Serial Ports...」を選びます。

<a href="http://www.flickr.com/photos/propella/7041977429/" title="bt3 by propella, on Flickr"><img src="http://farm8.staticflickr.com/7040/7041977429_c8886cfc0e.jpg" width="500" height="437" alt="bt3"></a>

すると 先ほどの SERVICE_NAME で指定した名前にちなんだシリアルポート名が作られます。もしも現れない時はこの画面で + を押すと出てくるので Apply して準備完了です。

** Bluetooth シリアルポートを実験

Mac のターミナルを広げ

>||
$ sudo screen /dev/tty.N-04D-BTHello 
||<

のようにして先程現れたシリアルポートを screen コマンドで指定すると Bluetooth に接続出来ます。まあこのプロラムは単にキー入力ごとに時刻を表示するだけです。

<a href="http://www.flickr.com/photos/propella/6895880464/" title="bt4 by propella, on Flickr"><img src="http://farm8.staticflickr.com/7187/6895880464_d90563130f_z.jpg" width="546" height="386" alt="bt4"></a>

終了後もしもターミナルが狂ったら

>||
$ stty sane
||<

とやって下さい。どうですか？結構面白い応用がありそうでしょう？

- Serial Port Profile http://www.palowireless.com/infotooth/tutorial/k5_spp.asp
- A2DP http://developer.bluetooth.org/KnowledgeCenter/TechnologyOverview/Pages/A2DP.aspx
