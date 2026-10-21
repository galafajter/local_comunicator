"""
Prosty P2P chat po UDP broadcast.
Brak serwera — każde uruchomienie tego skryptu jednocześnie
nasłuchuje i wysyła. Wszyscy w tej samej sieci Wi-Fi/LAN
na tym samym porcie widzą wszystkie wiadomości.

Uruchomienie:
    python simple_P2P.py TwojNick

Do przetestowania na jednym komputerze: otwórz dwa terminale
i uruchom z dwoma różnymi nickami.
"""

import socket
import threading
import sys
from datetime import datetime

PORT = 50000
BUFFER_SIZE = 1024


def listen(sock: socket.socket, my_name: str) -> None:
    """Wątek nasłuchujący — działa w tle przez cały czas życia programu."""
    while True:
        try:
            data, addr = sock.recvfrom(BUFFER_SIZE)
            message = data.decode("utf-8")

            # nie wypisuj własnych wiadomości, które wracają do nas przez broadcast
            sender_name = message.split(": ", 1)[0].split("] ")[1]
            if sender_name == my_name:
                continue
                # print(f"> \r{message.split(': ', 1)[1]}")
            else:
                print(f"\r{message}\n> ", end="")
        except OSError:
            # socket zamknięty przy wyjściu z programu
            break


def send_loop(sock: socket.socket, my_name: str) -> None:
    """Główna pętla — czyta z klawiatury i wysyła broadcast."""
    while True:
        try:
            text = input("> ")
        except (EOFError, KeyboardInterrupt):
            print("\nZamykanie...")
            break

        if text.strip() == "":
            continue
        if text.strip() == "/quit":
            break

        message = f"[{datetime.now().strftime('%H:%M')}] {my_name}: {text}"
        sock.sendto(message.encode("utf-8"), ("255.255.255.255", PORT))


def main() -> None:
    if len(sys.argv) < 2:
        print("Użycie: python simple_P2P.py TwojNick")
        sys.exit(1)

    my_name = sys.argv[1]

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_TTL, 3)
    sock.bind(("", PORT))

    print(f"Połączono jako '{my_name}'. Wpisz wiadomość i Enter. /quit aby wyjść.\n")

    listener_thread = threading.Thread(target=listen, args=(sock, my_name), daemon=True)
    listener_thread.start()

    send_loop(sock, my_name)

    sock.close()


if __name__ == "__main__":
    main()