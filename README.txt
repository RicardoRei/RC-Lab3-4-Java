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
	- Os ficheiros têm nomes simples, nomeadamente, que não incluem o caractere ‘-'.
	- Os utilizadores não têm credenciais de acesso.
	- Os utilizadores têm nomes simples, nomeadamente, que não incluem o caractere ‘-’.

Informação sobre o protocolo usado:

	Cliente:
		Inicia Sessão (1):
			- Envia um inteiro que representa o tamanho, em bytes, do username da sessão.
			- Envia o username da sessão.
			- Envia um inteiro que representa o tamanho, em bytes, do nome da directoria a sincronizar.
			- Envia o nome da directoria a sincronizar

		Sessão Inválida (3):
			- Caso a sessão seja inválidada, o cliente recebe uma mensagem de erro que detalha o problema, e desliga a conexão com o server, termninando o processo.

		Sessão Validada (3):
			- 

	Servidor:
		Valida Sessão (2):
			- Recebe os valores enviados no inicio de sessão do cliente e valida-os (verifica que a pasta desejada é do user a estabelecer sessão, e que não há outra sessão a sincronizar essa directoria)
			- Retorna uma mensagem com um inteiro (0, 1 ou 2) que significa (pedido validado / pedido invalidado porque o username especificado não corresponde ao dono da pasta especificada / pedido invalidado porque essa pasta já está a ser sincronizada noutra sessão pelo mesmo utilizador), respectivamente.