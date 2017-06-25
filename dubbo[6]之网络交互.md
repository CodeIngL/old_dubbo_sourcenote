## dubbo网络交互
上一篇我们详细介绍了dubbo是如何暴露网络服务，这一篇我们将对dubbo的网络通信交互细节作出详细的叙述。

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                //netty编码适配器
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                //设置pipeline
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        });

上面的代码，是上一篇文章贴过的代码中的片段，值得说明的是，我们网络交互的细节是采用默认netty来描述的。

### NettyCodecAdapter
---
这个是代码中已经说明，是编码解码器的适配。从代码来看，熟悉netty的童鞋一定会关注重点pipeline的设置。pipeline总共被设置了三个ChannelHandler，这里ChannelHandler
是netty框架的接口，而不是dubbo的抽象接口

#### InternalDecoder
---
该类是解码handler也就是代码中的**adapter.getDecoder()**的返回结果。

该类继承了netty中的简单上行处理类，值得注意的该类一通道一实例，避免了并发问题。

该类在解码上做了很多事情啊，包括协议解析，TCP拆包粘包问题处理，对象的序列化。

	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {

			//见下面说明1,2两点

			//见3点
            message;

            //本地buffer中有数据
            if (buffer.readable()) {
                //buffer是DynamicChannelBuffer类型
                //直接写入追加,一个协议包需要至少跨越3三个TCP包承载后发生
                if (buffer instanceof DynamicChannelBuffer) {
                    buffer.writeBytes(input.toByteBuffer());
                    message = buffer;
                } else {
                    //将普通的message实现转化为DynamicChannelBuffer实现，实现处理tcp拆包现象

                    //获得总共大小
                    int size = buffer.readableBytes() + input.readableBytes();
                    //创建DynamicChannelBuffer
                    message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(
                        size > bufferSize ? size : bufferSize);
                    //将buffer中的东西写入message
                    message.writeBytes(buffer, buffer.readableBytes());
                    //将input中的东西写入message
                    message.writeBytes(input.toByteBuffer());
                }
            } else {
                //是一个数据包开始，因为没有buffer
                //包装netty的buffer为dubbo内部统一的结构处理
                message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.wrappedBuffer(
                    input.toByteBuffer());
            }

			//见第5点
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);

            Object msg;
            int saveReaderIndex;
            try {
                // decode object.
                do {
                    //buffer有效的可读位置
                    saveReaderIndex = message.readerIndex();
                    try {
                        //通过传递包装的参数（nettyChannel和ChannelBuffers的实现类）
                        // 尝试解码TCP包中的数据，从而获得对象
                        msg = codec.decode(channel, message);
                    } catch (IOException e) {
                        //出错的处理
                        buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                        throw e;
                    }
                    //解码或获得对象是NEED_MORE_INPUT，也就是需要获得更多数据包完成数据的处理
                    if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                        //重置读位置的光标
                        message.readerIndex(saveReaderIndex);
                        break;
                    } else {
                        //校验，先前的读位置的光标和解码之后光标的位置
                        if (saveReaderIndex == message.readerIndex()) {
                            buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                            throw new IOException("Decode without read data.");
                        }
                        if (msg != null) {
                            //传递事件
                            Channels.fireMessageReceived(ctx, msg, event.getRemoteAddress());
                        }
                    }
                } while (message.readable());//下一个可用数据
            } finally {
                if (message.readable()) {
                    message.discardReadBytes();
                    buffer = message;
                } else {
                    buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                }
                NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
            }
        }
这个是进行通讯时候，处理上行消息时的方法，不清楚的童鞋可以简单的学一下netty。
这个方法代码很长。做的事情也很多，我们一一道来

1. 校验事件的类型，对于事件对象不是ChannelBuffer直接传递给下一个Handler

2. 检验通道缓冲区中可读字节的情况，可读字节数量readable表示，数量不合理，直接返回

3. 创建dubbo框架抽象通道缓冲区：message

4. 对实例数据缓存buffer进行处理，该结构是为了保存发生粘包拆包现象的TCP数据。
	1. buffer中有数据的情况：
		1. 根据buffer的类型进行处理
			1. DynamicChannelBuffer类型下，直接将netty字节缓存区写入缓存buffer中，将buffer赋值给message
				- 是dubbo协议，但至少跨越三个TCP包承载后发生
				- 或者是telnet协议处理
		2. 获得总共可读大小，缓存buffer中和netty缓冲区中
		3. 新建message为DynamicChannelBuffer类型，并读入缓存buffer和netty缓冲区的数据
	
	2. 其他情况，根据netty的字节缓冲区的不同情况封装不同抽象实例
		1. netty字节缓冲区无效，返回空HeapChannelBuffer
		2. netty字节缓冲区有备份数组，返回喊数组拷贝的HeapChannelBuffer
		3. 返回ByteBufferBackedChannelBuffer

5. 尝试获得nettyChannel，并发安全，没有则新建

6. 对抽象通道缓冲区实例message进行循环处理
	1. 保存message的起始读偏移量

	2. **使用属性codec对应类型进行解码，返回Object类型实例msg**

	3. 对于msg是还需要读取的实例（NEED_MORE_INPUT），进行重置起始读偏移量，并跳出循环，进入第7点

	4. 对应其他合法的msg，传递事件给下一个handler处理
		1. 合法的标志意味着读坐标发生了变化，这是检查合法的方式
	
7. 完成message和缓存buffer的转换
	1. message可读，读写坐标转换后，直接付给buffer
	2. message其他状态，buffer直接EMPTY_BUFFER

整个解码的过程就此结束了，但我们却没还没看到细节的处理，比如自定义协议处理，序列化处理，粘包拆包的具体处理。当然这一切的一切我们都会娓娓道来，我已经对上面需要的关注做出了加粗说明，也就是6.2，使用属性codec进行解码，默认情况下是用DubboCountCodec来处理。如何默认请看我的dubbo杂点一文

#### DubboCountCodec.decode
---
该解码器的功能是为了尽量尝试解码出多个消息

    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        
		//第一步见1

        //多个消息结构
        result;

        do {
            Object obj = codec.decode(channel, buffer);
            //返回是NEED_MORE_INPUT，说明要读取下个TCP包来获取完整协议
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                //更新位置信息
                buffer.readerIndex(save);
                break;
            } else {
                //添加进result
                result.addMessage(obj);
                logMessageLength(obj, buffer.readerIndex() - save);
                //更新位置
                save = buffer.readerIndex();
            }
        } while (true);
        //对于消息对象为0的情况处理
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }
        //对于消息对象为1的情况处理
        if (result.size() == 1) {
            return result.get(0);
        }
        //对于消息对象为多个的情况处理
        return result;
    }

我们介绍下整个逻辑	。

1. 第一步总是保存当前的读偏移量

2. 创建多个消息的载体结构result

3. 大循环
	1. **委托给被代理对象codec进行解码**
	
	2. 对解码返回值处理
		- 解码对象是NEED_MORE_INPUT，突破循环
		- 解码是其他对象
			- 加入消息载体
			- 对序列化对象的内部数据，添加属性键值对说明（key由实际类型决定和value为包长）
		- 更新读便宜量
4. 对消息载体的处理
	1. 内部没有数据，返回解码对象NEED_MORE_INPUT
	2. 只有一个对象，返回对象
	3. 多个对象，返回消息载体

整体逻辑总式介绍完了，细心读者应该能发现超前的知识，还没出现序列化的相关代码，我却提到了序列化相关。我们继续关注加粗的部分，现在这个codec实际上是DubboCodec


#### ExchangeCodec.decode
---
上面说的实际的codeC是DubboCodec，该解码器的功能是为了解码单个包，实际上在该java类中找不到decode函数，他使用了父类默认的decode方法，也就是**ExchangeCodec.decode**

    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        //获取buffer可读的一部分
        int readable = buffer.readableBytes();
        //尝试获取协议头部，协议头部是16个字节
        //协议头部不一定是完整的（ex:TCP拆包）
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        return decode(channel, buffer, readable, header);
    }

直接来解释代码吧，代码那么短。

1. 获得buffer中可读的数据量readable
2. 尝试构建头部，为什么叫尝试呢
	- dubbo协议头是了16个字节，当然很有可能可读的数量还少于16个字节，典型的就是使用telnet协议访问
	- 因此使用readable和HEADER_LENGTH中的较小值来构建
3. 尝试读取头部，为什么叫尝试，上面我们说过了
4. 委托给其他方法 decode(channel, buffer, readable, header)

#### ExchangeCodec.decode(channel, buffer, readable, header)
---
这个方法就是委托的方法，代码示意如下：

	protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        //检查魔数，2个字节,头部的前两个字节
        if (readable > 0 && header[0] != MAGIC_HIGH
                || readable > 1 && header[1] != MAGIC_LOW) {
            //不包含魔数的处理，说明是TCP拆包造成的
            int length = header.length;
            //尝试把readable全部读出来，该部分数据是属于上一个TCP包
            if (header.length < readable) {
                //获得新的字节数组
                header = Bytes.copyOf(header, readable);
                //读入数据
                buffer.readBytes(header, length, readable - length);
            }

            //在新的字节数组中，寻找下一个魔数的位置（dubbo协议包的位置）
            for (int i = 1; i < header.length - 1; i ++) {
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    //重置读标签的位置
                    buffer.readerIndex(buffer.readerIndex() - header.length + i);
                    //header为当前数据到下一个协议包的位置的字节数组
                    header = Bytes.copyOf(header, i);
                    break;
                }
            }
            //header代表的包含协议，或者包长过长，还未包含的处理
            return super.decode(channel, buffer, readable, header);
        }

        //检查长度，长度小于协议头部，说明是拆包，还需要继续读取
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        //获得头部中代表数据包长度的字段
        int len = Bytes.bytes2int(header, 12);
        checkPayload(channel, len);

        //整个协议包
        int tt = len + HEADER_LENGTH;
        //buffer中的小于整个协议包长度，
        //需要再读取
        if( readable < tt ) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // limit input stream.
        ChannelBufferInputStream is = new ChannelBufferInputStream(buffer, len);

        try {
            //解析协议数据部分
            return decodeBody(channel, is, header);
        } finally {
            if (is.available() > 0) {
                try {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip input stream " + is.available());
                    }
                    StreamUtils.skipUnusedStream(is);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
    }
代码老长，老长。还是慢慢解释其中的设计，而不是单纯说明代码

1. 不含魔数的处理（可能是TCP拆包粘包造成的，使用telnet协议也会造成），魔数是dubbo协议头的前两个字节，而魔数的概念，不明白的可以搜索下。
	1. 尝试将buffer中的内容全部读入header
		- 之前说明header是为了尝试读取头部，既然现在不符合头部，那么久尽量将buffer中的数据取出来先
	2. 尝试寻找下一个魔数的坐标，为什么是尝试呢，这里无法区分是TCP拆包粘包还是telnet协议
		- 找到后，对buffer的读位置进行转换，换到下一个魔数的位置
		- header转换，header代表起始位置到下一个魔数的位置，因为我们要处理这部分header了
	3. 调用父类的decode，值得说明的是父类是TelnetCodec，意味着其实针对telnet的处理的，那么由dubbo协议相关的TCP粘包拆包怎么处理呢，我们慢慢道来

2. 检查可读数据和协议头的大小（说明是dubbo协议包，且是从头开始的包），
	- 可读数据小于协议头的自然要直接返回了

3. 读取包头中的的数据长度len，12个字节到16个字节表示

4. 检查包大小是否符合要求

5. 比较可读数量和整个协议包大小
	- 小于整个包长，自然要返回了，说明是dubbo协议包过大，发生拆包现象

6. 包装代表一个dubbo协议包的结构ChannelBufferInputStream实例is

7. 解码协议包payload部分


#### ExchangeCodec.decodeBody(Channel channel, InputStream is, byte[] header)
---
该方法就是上面所说的解码协议包的payload部分，值得说明的是，现在参数is代表了payload的输入流
header是完整的头，这两个参数相加就是完整的一个协议包，注意是**一个协议包**，代码示意如下:

	protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        //头部第三个字节
        //包含序列化协议标识，event标识, two way标识，REQ/res标识
        byte flag = header[2];

        //使用掩码00011111获得使用的序列化协议
        byte proto = (byte) (flag & SERIALIZATION_MASK);

        //获得序列化协议对象，使用什么方式去序列化
        Serialization s = CodecSupport.getSerialization(channel.getUrl(), proto);
        ObjectInput in = s.deserialize(channel.getUrl(), is);

        //协议32位以后
        //get request id.
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) {
            //23位标志位是response的处理
            //decode response.
            //解码回复的id
            Response res = new Response(id);
            //代表心跳，event标志位为1
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);
            }
            //获得状态头
            // get status.
            byte status = header[3];
            //设置状态头
            res.setStatus(status);
            //状态是OK
            if (status == Response.OK) {
                try {
                    Object data;
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, in);
                    } else if (res.isEvent()) {
                        data = decodeEventData(channel, in);
                    } else {
                        data = decodeResponseData(channel, in, getRequestData(id));
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {
                res.setErrorMessage(in.readUTF());
            }
            return res;
        } else {
            // decode request.
            //解码请求，23位标志位是1
            Request req = new Request(id);
            //设定版本
            req.setVersion("2.0.0");
            //设定twoWay
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            //设定事件
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                if (req.isHeartbeat()) {//事件标志位是1
                    data = decodeHeartbeatData(channel, in);
                } else if (req.isEvent()) {//是其他事件，不是心跳的处理
                    data = decodeEventData(channel, in);
                } else {
                    //其他
                    data = decodeRequestData(channel, in);
                }
                req.setData(data);
            } catch (Throwable t) {
                // bad request
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

代码很长，但是是一个典型的解析包的过长，根据头部信息解析payload部分。我详细来介绍下

1. 获得头部的第二个字节flag
	- 5位采用的序列化协议标识
	- 1位event标识
	- 1位TwoWay标识
	- 1位REQ/RES标识

2. 获得序列化协议标识 proto

3. 获得相应的序列化协议标识对象s

4. 获得抽象的序列化输入对象in

5. 获得头部中的id字段，长64位，使用long来接收

6. 标志位为RES的处理（response）：产生Response的实例res
	1. 为res设置id
	
	2. 为res设置HEARTBEAT_EVENT的event，如果event标识是1的话
	
	3. 为res设置status，字段值来自协议头部第三字节
	
	4. 根据status的处理，status要求是Response.OK
		1. 根据res的情况进行不同的处理
			1. res.isHeartbeat()
			2. res.isEvent()
			3. res.isEvent()
	5. status不是Response.OK，或者反序列化过程出了错，都会进行设置相应错误
		- 反序列过程扔出的异常处理
			- 设置res的status为90（client_error说明客户端有误）
			- 设置res的错误消息为异常内容
		- 其他
			- 设置res的错误消息为payload中的内容
7. 标志位为REQ的处理（request）：产生Request的实例req


### nettyHandler的处理
---
上面我们说明的都是解码handler的处理，当解码handler序列化好对象后，进入下一步事件，自然就是被传递给nettyHandler的处理。我们来详细的说明下

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);
        try {
            handler.received(channel, e.getMessage());
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
        }
    }
代码很简单，但是实际逻辑确实很复杂，上面我们已经说过，这个handler不知道嵌套包装了多少个handler。

1. 最外层NettyServer该类也实现了ChannelHandler方法，他使用父类的默认实现，也就是AbstractPeer的实现，做的事情也很简单，简单传递给内部ChannelHandler处理
2. MultiMessageHandler的处理
	1. 简单的处理多消息
3. HeartbeatHandler的处理
	1. 心跳处理
4. AllChannelHandler的处理
	1. 提到线程处理
5. DecodeHandler的处理
	1. 解码message的各种类型
6. HeaderExchangeHandler的处理
	1. 根据message类型处理
7. ExchangeHandlerAdapter的处理
	1. 对于message是Invocation进行处理，反之什么也不干。


### ExchangeHandlerAdapter.reply处理message
---
这里是最后一步的处理。
