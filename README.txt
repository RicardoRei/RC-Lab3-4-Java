Projecto desenvolvido no âmbito da disciplina Redes de Computadores, de 3º ano / 1º semestre, no Instituto Superior Técnico por:
	Diogo Cortez nrº 78012
	Frederico Monteiro nrº 78021
	Ricardo Rei nrº 78047

Como correr o projecto:

- Compilar as classes

- Num terminal lançar o servidor com o comando:
	"java Caixote_Server <port>"

	Onde:
	<port> é a porta em que o servidor vai ficar à escuta de clientes. Recomenda-se usar a porta nrº 40.

- Num outro terminal lançar o cliente com o comando:
	"java Caixote_client <hostname> <port> <username> <directoria>"

	Onde:
	<hostname> terá que ser o endereço de IP da máquina onde foi executado o servidor.
	<port> terá que ser a mesma port escolhida no servidor, no comando do servidor.
	<username> é o username desejado para ser usado nesta sessão.
	<directoria> é a directoria que se deseja sincronizar com o servidor.

Especiais atenções:
	- Os ficheiros têm nomes simples, nomeadamente, que não incluem o caractere ‘-’.
	- Os utilizadores não têm credenciais de acesso.

Informação sobre o protocolo usado:
