import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import './App.css'

type Player = {
  nickname: string
  host: boolean
  ready: boolean
}

type RoomConfig = {
  roundCount: number
  coinPrice: number
  turnDurationSeconds: number
}

type Room = {
  roomCode: string
  phase: string
  visibility: 'PUBLIC' | 'PRIVATE'
  players: Player[]
  config: RoomConfig
}

type PublicRoom = {
  roomCode: string
  hostNickname: string
  playerCount: number
  maxPlayers: number
  passwordRequired: boolean
  config: RoomConfig
}

type RoomEvent = {
  type: string
  payload: unknown
  occurredAt: string
}

type ChatMessage = {
  nickname: string
  message: string
  sentAt: string
}

type JoinResponse = {
  roomCode: string
  nickname: string
  sessionToken: string
}

const sessionStorageKey = 'rogue-nest-session'

function readStoredJoin(): JoinResponse | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(sessionStorageKey) ?? 'null')
    if (
      typeof value === 'object'
      && value !== null
      && 'roomCode' in value
      && typeof value.roomCode === 'string'
      && 'nickname' in value
      && typeof value.nickname === 'string'
      && 'sessionToken' in value
      && typeof value.sessionToken === 'string'
    ) {
      return {
        roomCode: value.roomCode,
        nickname: value.nickname,
        sessionToken: value.sessionToken,
      }
    }
  } catch {
    sessionStorage.removeItem(sessionStorageKey)
  }
  return null
}

async function request<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const data: unknown = await response.json()
  if (!response.ok) {
    const error = data as { message?: string }
    throw new Error(error.message ?? `요청 실패 (${response.status})`)
  }
  return data as T
}

function App() {
  const [nickname, setNickname] = useState('')
  const [myNickname, setMyNickname] = useState('')
  const [password, setPassword] = useState('')
  const [isPublic, setIsPublic] = useState(true)
  const [roomCodeInput, setRoomCodeInput] = useState('')
  const [publicRooms, setPublicRooms] = useState<PublicRoom[]>([])
  const [refreshingRooms, setRefreshingRooms] = useState(false)
  const [room, setRoom] = useState<Room | null>(null)
  const [chat, setChat] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [connection, setConnection] = useState<'idle' | 'connecting' | 'connected' | 'reconnecting'>('idle')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [restoreJoin] = useState(readStoredJoin)
  const clientRef = useRef<Client | null>(null)
  const identityRef = useRef<Pick<JoinResponse, 'roomCode' | 'nickname' | 'sessionToken'> | null>(null)

  const refreshPublicRooms = useCallback(async () => {
    setRefreshingRooms(true)
    try {
      setPublicRooms(await request<PublicRoom[]>('/api/rooms/public'))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '공개 방 목록을 불러오지 못했습니다.')
    } finally {
      setRefreshingRooms(false)
    }
  }, [])

  useEffect(() => () => {
    void clientRef.current?.deactivate()
  }, [])

  useEffect(() => {
    const initialRefresh = window.setTimeout(() => void refreshPublicRooms(), 0)
    const interval = window.setInterval(() => void refreshPublicRooms(), 10_000)
    return () => {
      window.clearTimeout(initialRefresh)
      window.clearInterval(interval)
    }
  }, [refreshPublicRooms])

  const handleRoomEvent = useCallback((frame: IMessage) => {
    const event = JSON.parse(frame.body) as RoomEvent
    if (event.type === 'room') {
      setRoom(event.payload as Room)
    } else if (event.type === 'chat') {
      setChat((messages) => [...messages.slice(-99), event.payload as ChatMessage])
    } else if (event.type === 'snapshot') {
      const snapshot = event.payload as { room: Room }
      setRoom(snapshot.room)
    }
  }, [])

  const connectToRoom = useCallback((join: JoinResponse) => {
    sessionStorage.setItem(sessionStorageKey, JSON.stringify(join))
    identityRef.current = join
    setMyNickname(join.nickname)
    setRoomCodeInput(join.roomCode)
    setChat([])
    setConnection('connecting')
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      connectHeaders: { 'x-session-token': join.sessionToken },
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 3_000,
      onConnect: () => {
        setConnection('connected')
        setError('')
        client.subscribe(`/topic/room/${join.roomCode}`, handleRoomEvent)
        client.subscribe('/user/queue/private', handleRoomEvent)
        client.publish({
          destination: `/app/rooms/${join.roomCode}/snapshot`,
          body: '{}',
        })
      },
      onWebSocketClose: () => {
        setConnection((current) => current === 'idle' ? 'idle' : 'reconnecting')
      },
      onStompError: (frame) => {
        const message = frame.headers.message ?? 'WebSocket 요청 처리 중 오류가 발생했습니다.'
        if (frame.body.includes('INVALID_SESSION') || frame.body.includes('ROOM_NOT_FOUND')) {
          sessionStorage.removeItem(sessionStorageKey)
          identityRef.current = null
          setRoom(null)
          setConnection('idle')
          setError('재접속할 수 있는 시간이 지났거나 세션이 만료됐습니다. 다시 입장해 주세요.')
          return
        }
        setError(message)
      },
      onWebSocketError: () => {
        setError('WebSocket 연결에 실패했습니다. 백엔드 서버가 실행 중인지 확인해 주세요.')
      },
    })
    clientRef.current = client
    client.activate()
  }, [handleRoomEvent])

  useEffect(() => {
    if (!restoreJoin) return
    let cancelled = false
    const timeout = window.setTimeout(() => {
      if (!cancelled) connectToRoom(restoreJoin)
    }, 0)
    return () => {
      cancelled = true
      window.clearTimeout(timeout)
    }
  }, [connectToRoom, restoreJoin])

  const createRoom = async () => {
    setBusy(true)
    setError('')
    try {
      const created = await request<{ roomCode: string }>('/api/rooms', {
        password,
        visibility: isPublic ? 'PUBLIC' : 'PRIVATE',
      })
      const joined = await request<JoinResponse>(`/api/rooms/${created.roomCode}/join`, {
        nickname,
        password,
      })
      connectToRoom(joined)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '방을 만들지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const joinRoom = async (requestedRoomCode = roomCodeInput) => {
    setBusy(true)
    setError('')
    try {
      const joined = await request<JoinResponse>(
        `/api/rooms/${requestedRoomCode.trim().toUpperCase()}/join`,
        { nickname, password },
      )
      connectToRoom(joined)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '방에 입장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const publish = (action: string, body: unknown) => {
    const current = identityRef.current
    const client = clientRef.current
    if (!current || !client?.connected) {
      setError('WebSocket에 연결된 뒤 다시 시도해 주세요.')
      return
    }
    client.publish({
      destination: `/app/rooms/${current.roomCode}/${action}`,
      body: JSON.stringify(body),
    })
  }

  const sendChat = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!chatInput.trim()) return
    publish('chat', { message: chatInput.trim() })
    setChatInput('')
  }

  const leaveRoom = async () => {
    const client = clientRef.current
    clientRef.current = null
    identityRef.current = null
    sessionStorage.removeItem(sessionStorageKey)
    setRoom(null)
    setChat([])
    setConnection('idle')
    if (client) await client.deactivate()
  }

  const myPlayer = room?.players.find((player) => player.nickname === myNickname)
  const isConnected = connection === 'connected'

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="RogueNest 시작 화면">
          <span className="brand-mark">R</span>
          <span>ROGUE<span className="brand-accent">NEST</span></span>
        </a>
        <div className="topbar-note"><span className="live-dot" /> REALTIME LOBBY TEST</div>
      </header>

      {!room ? (
        <section className="entry-layout">
          <div className="intro-panel">
            <span className="eyebrow">A SOCIAL DEDUCTION GAME</span>
            <h1>누가 도적일까<span className="title-period">.</span></h1>
            <p>방을 만들거나 코드로 입장해 대기실 WebSocket 연결을 테스트해 보세요.</p>
            <div className="feature-row">
              <span><i>01</i> 방 생성 / 입장</span>
              <span><i>02</i> 실시간 참가자</span>
              <span><i>03</i> 대기실 채팅</span>
            </div>
          </div>
          <section className="panel entry-card">
              <div className="panel-heading">
                <div><span className="eyebrow">GET IN THE GAME</span><h2>대기실 입장</h2></div>
                <span className="small-icon">↗</span>
              </div>
              <label className="field-label" htmlFor="nickname">닉네임</label>
              <input id="nickname" value={nickname} maxLength={20} onChange={(event) => setNickname(event.target.value)} placeholder="게임에서 사용할 이름" />
              <label className="field-label" htmlFor="password">방 비밀번호 <span className="optional">선택</span></label>
              <input id="password" value={password} maxLength={100} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호가 없다면 비워두세요" type="password" />
              <div className="visibility-choice" role="group" aria-label="새 방 공개 여부">
                <button type="button" className={isPublic ? 'selected' : ''} onClick={() => setIsPublic(true)}>공개 방</button>
                <button type="button" className={!isPublic ? 'selected' : ''} onClick={() => setIsPublic(false)}>비공개 방</button>
              </div>
              <button className="button button-primary full-button" disabled={busy || !nickname.trim()} onClick={() => void createRoom()}>
                {busy ? '처리 중...' : '새 방 만들기'} <span>→</span>
              </button>
              <div className="separator"><span>또는 방 코드로 입장</span></div>
              <div className="join-row">
                <input aria-label="방 코드" value={roomCodeInput} maxLength={6} onChange={(event) => setRoomCodeInput(event.target.value.toUpperCase())} placeholder="방 코드" />
                <button className="button button-secondary" disabled={busy || !nickname.trim() || !roomCodeInput.trim()} onClick={() => void joinRoom()}>입장</button>
              </div>
              {error && <p className="error-message" role="alert">{error}</p>}
              <p className="privacy-note"><span>●</span> 세션은 현재 탭에서 새로고침해도 유지됩니다.</p>
          </section>

          <section className="panel public-rooms-panel">
              <div className="public-rooms-heading">
                <div><span className="eyebrow">OPEN TABLES</span><h2>공개 대기실</h2></div>
                <button className="refresh-button" disabled={refreshingRooms} onClick={() => void refreshPublicRooms()} aria-label="공개 방 새로고침">{refreshingRooms ? '...' : '↻'}</button>
              </div>
              {publicRooms.length === 0 ? (
                <p className="public-empty">열려 있는 공개 방이 없습니다.</p>
              ) : (
                <div className="public-room-list">
                  {publicRooms.map((publicRoom) => (
                    <article className="public-room-row" key={publicRoom.roomCode}>
                      <div className="public-room-info">
                        <strong>{publicRoom.roomCode}</strong>
                        <span>{publicRoom.hostNickname} · {publicRoom.playerCount}/{publicRoom.maxPlayers}명</span>
                      </div>
                      {publicRoom.passwordRequired && <span className="locked-tag" title="비밀번호 필요">잠금</span>}
                      <button className="button button-secondary public-join-button" disabled={busy || !nickname.trim()} onClick={() => {
                        setRoomCodeInput(publicRoom.roomCode)
                        if (publicRoom.passwordRequired) {
                          setError('이 방은 비밀번호가 필요합니다. 위 입력란에 비밀번호를 입력한 뒤 방 코드로 입장해 주세요.')
                          document.getElementById('password')?.focus()
                        } else {
                          void joinRoom(publicRoom.roomCode)
                        }
                      }}>입장</button>
                    </article>
                  ))}
                </div>
              )}
          </section>
        </section>
      ) : (
        <section className="lobby-layout">
          <div className="lobby-main">
            <div className="room-heading">
              <div>
                <span className="eyebrow">WAITING ROOM</span>
                <h1>방 <span className="room-code">{room.roomCode}</span></h1>
                <p>친구에게 방 코드를 공유하고 함께 입장하세요.</p>
              </div>
              <div className={`connection-pill ${connection}`}>
                <span className="connection-dot" />
                {connection === 'connected' ? '연결됨' : connection === 'connecting' ? '연결 중' : '재연결 중'}
              </div>
            </div>

            <section className="panel players-panel">
              <div className="section-heading">
                <div><span className="eyebrow">THE TABLE</span><h2>참가자 <span className="count">{room.players.length}/12</span></h2></div>
                <span className="phase-tag">{room.phase}</span>
              </div>
              <div className="player-list">
                {room.players.map((player, index) => (
                  <article className={`player-row ${player.nickname === myPlayer?.nickname ? 'is-me' : ''}`} key={player.nickname}>
                    <span className="player-number">{String(index + 1).padStart(2, '0')}</span>
                    <span className="avatar">{player.nickname.slice(0, 1)}</span>
                    <span className="player-name">{player.nickname}{player.nickname === myPlayer?.nickname && <small>나</small>}</span>
                    <span className={`player-badge ${player.host ? 'host-badge' : player.ready ? 'ready-badge' : ''}`}>
                      {player.host ? '방장' : player.ready ? '준비 완료' : '대기 중'}
                    </span>
                  </article>
                ))}
                {room.players.length < 4 && <p className="waiting-hint">게임 시작까지 {4 - room.players.length}명이 더 필요해요.</p>}
              </div>
              <div className="lobby-actions">
                {!myPlayer?.host && (
                  <button className={`button ${myPlayer?.ready ? 'button-ready' : 'button-secondary'}`} disabled={!isConnected} onClick={() => publish('ready', !myPlayer?.ready)}>
                    {myPlayer?.ready ? '준비 취소' : '준비 완료'}
                  </button>
                )}
                {myPlayer?.host && (
                  <button className="button button-primary" disabled={!isConnected || room.players.length < 4} onClick={() => publish('start', {})}>
                    게임 시작 <span>→</span>
                  </button>
                )}
                <button className="button button-quiet" onClick={() => void leaveRoom()}>나가기</button>
              </div>
              {myPlayer?.host && (
                <div className="settings-row">
                  <label>라운드 <select value={room.config.roundCount} disabled={!isConnected} onChange={(event) => publish('settings', { config: { ...room.config, roundCount: Number(event.target.value) } })}>{[3, 5, 7, 10].map((value) => <option key={value} value={value}>{value} 라운드</option>)}</select></label>
                  <label>코인 가격 <select value={room.config.coinPrice} disabled={!isConnected} onChange={(event) => publish('settings', { config: { ...room.config, coinPrice: Number(event.target.value) } })}>{[20, 30, 50, 70, 100].map((value) => <option key={value} value={value}>{value}G</option>)}</select></label>
                  <label>턴 시간 <select value={room.config.turnDurationSeconds} disabled={!isConnected} onChange={(event) => publish('settings', { config: { ...room.config, turnDurationSeconds: Number(event.target.value) } })}>{[60, 90, 120, 150, 180].map((value) => <option key={value} value={value}>{value}초</option>)}</select></label>
                </div>
              )}
            </section>
            <div className="connection-footnote"><span className="live-dot" /> STOMP · 10s heartbeat <span className="footnote-divider">/</span> session authenticated</div>
          </div>

          <aside className="panel chat-panel">
            <div className="section-heading chat-heading">
              <div><span className="eyebrow">TALK TO THE TABLE</span><h2>대기실 채팅</h2></div>
              <span className="chat-count">{chat.length}</span>
            </div>
            <div className="chat-messages" aria-live="polite">
              {chat.length === 0 ? (
                <div className="chat-empty"><span>✳</span><p>아직 메시지가 없어요.</p><small>첫 인사를 건네보세요.</small></div>
              ) : chat.map((message, index) => (
                <article className={`chat-message ${message.nickname === myPlayer?.nickname ? 'my-message' : ''}`} key={`${message.sentAt}-${index}`}>
                  <div className="chat-meta"><strong>{message.nickname}</strong><time>{new Date(message.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</time></div>
                  <p>{message.message}</p>
                </article>
              ))}
            </div>
            <form className="chat-form" onSubmit={sendChat}>
              <input aria-label="채팅 메시지" value={chatInput} maxLength={500} disabled={!isConnected} onChange={(event) => setChatInput(event.target.value)} placeholder={isConnected ? '메시지 입력...' : '연결 중...'} />
              <button className="send-button" disabled={!isConnected || !chatInput.trim()} aria-label="메시지 보내기">↑</button>
            </form>
            {error && <p className="error-message chat-error" role="alert">{error}</p>}
          </aside>
        </section>
      )}
      <footer className="page-footer"><span>ROGUE NEST</span><span>THE GAME IS ON THE SERVER.</span></footer>
    </main>
  )
}

export default App
